package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.BoolQuery;
import org.col.es.query.CollapsibleList;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.NameUsageTransfer.extractClassifiction;
import static org.col.es.SynonymResultHandler.KeyType.DATASET;

/**
 * Collects synonyms from Postgres/MyBatis until and adds their classification before inserting them into Elasticsearch.
 */
final class SynonymResultHandler implements ResultHandler<NameUsageWrapper>, AutoCloseable {

  static enum KeyType {
    DATASET, SECTOR
  }

  private static final Logger LOG = LoggerFactory.getLogger(SynonymResultHandler.class);
  // The number of distinct taxa to collect per cycle
  private static final int LOOKUP_TABLE_SIZE = 4096;

  private final List<String> taxonIds = new ArrayList<>(LOOKUP_TABLE_SIZE);
  private final List<NameUsageWrapper> collected = new ArrayList<>(LOOKUP_TABLE_SIZE * 2);

  private final NameUsageIndexer indexer;
  private final int key;
  private final KeyType keyType;

  private String prevTaxonId = "";

  SynonymResultHandler(NameUsageIndexer indexer, int key) {
    this(indexer, key, KeyType.DATASET);
  }

  SynonymResultHandler(NameUsageIndexer indexer, int key, KeyType keyType) {
    this.indexer = indexer;
    this.key = key;
    this.keyType = keyType;
  }

  @Override
  public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
    handle(ctx.getResultObject());
  }

  @VisibleForTesting
  void handle(NameUsageWrapper nuw) {
    collected.add(nuw);
    String taxonId = getTaxonId(nuw);
    if (!taxonId.equals(prevTaxonId)) {
      // N.B. synonyms presumed to be ordered by taxon ID in NameUsageMapper.xml
      taxonIds.add(prevTaxonId = taxonId);
      if (taxonIds.size() == LOOKUP_TABLE_SIZE) {
        try {
          flush();
        } catch (IOException e) {
          throw new EsException(e);
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (collected.size() != 0) {
      flush();
    }
  }

  private void flush() throws IOException {
    LOG.debug("Loading taxa");
    List<EsNameUsage> taxa = loadTaxa();
    LOG.debug("Building lookup table for {} taxa", taxa.size());
    HashMap<String, List<SimpleName>> lookups = createLookupTable(taxa);
    LOG.debug("Copying classifications");
    collected.forEach(nuw -> {
      String taxonId = getTaxonId(nuw);
      nuw.setClassification(lookups.get(taxonId));
    });
    LOG.debug("Submitting {} synonyms to indexer", collected.size());
    indexer.accept(collected);
    taxonIds.clear();
    collected.clear();
    prevTaxonId = "";
  }

  private List<EsNameUsage> loadTaxa() throws IOException {
    NameUsageSearchService svc = new NameUsageSearchService(indexer.getIndexName(), indexer.getEsClient());
    String field = keyType == DATASET ? "datasetKey" : "sectorKey";
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery(field, key))
        .filter(new TermsQuery("usageId", taxonIds));
    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    // Keep memory footprint of lookup table as small as possible:
    esr.select("usageId", "classificationIds", "classification");
    esr.setQuery(query);
    esr.setSort(CollapsibleList.of(SortField.DOC));
    esr.setSize(taxonIds.size());
    return svc.getDocuments(indexer.getIndexName(), esr);
  }

  private static HashMap<String, List<SimpleName>> createLookupTable(List<EsNameUsage> taxa) {
    HashMap<String, List<SimpleName>> map = new HashMap<>(taxa.size(), 1F);
    taxa.forEach(enu -> map.put(enu.getUsageId(), extractClassifiction(enu)));
    return map;
  }

  private static String getTaxonId(NameUsageWrapper nuw) {
    return ((Synonym) nuw.getUsage()).getAccepted().getId();
  }

}
