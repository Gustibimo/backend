package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectReader;

import org.col.api.model.Page;
import org.col.api.search.FacetValue;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.response.EsFacet;
import org.col.es.response.EsFacetsContainer;
import org.col.es.response.EsNameSearchResponse;
import org.col.es.response.SearchHit;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.FIELD;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.NAME_INDEX_ID;
import static org.col.api.search.NameSearchParameter.NOM_STATUS;
import static org.col.api.search.NameSearchParameter.PUBLISHED_IN_ID;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;
import static org.col.api.search.NameSearchParameter.TYPE;

/**
 * Converts the Elasticsearch response object to a NameSearchResponse instance.
 */
class NameSearchResponseTransfer {

  private final EsNameSearchResponse esRresponse;
  private final Page page;

  NameSearchResponseTransfer(EsNameSearchResponse response, Page page) {
    this.esRresponse = response;
    this.page = page;
  }

  public NameSearchResponse transferResponse() throws IOException {
    int total = esRresponse.getHits().getTotal();
    List<NameUsageWrapper> nameUsages = transferNameUsages();
    if (esRresponse.getAggregations() == null) {
      return new NameSearchResponse(page, total, nameUsages);
    }
    return new NameSearchResponse(page, total, nameUsages, transferFacets());
  }

  private List<NameUsageWrapper> transferNameUsages() throws IOException {
    List<SearchHit<EsNameUsage>> hits = esRresponse.getHits().getHits();
    List<NameUsageWrapper> nus = new ArrayList<>(hits.size());
    ObjectReader reader = EsModule.NAME_USAGE_READER;
    for (SearchHit<EsNameUsage> hit : hits) {
      String payload = hit.getSource().getPayload();
      nus.add((NameUsageWrapper) reader.readValue(payload));
    }
    return nus;
  }

  private Map<NameSearchParameter, Set<FacetValue<?>>> transferFacets() {
    EsFacetsContainer esFacets = esRresponse.getAggregations().getContextFilter().getFacetsContainer();
    Map<NameSearchParameter, Set<FacetValue<?>>> facets = new EnumMap<>(NameSearchParameter.class);
    addIfPresent(facets, DATASET_KEY, esFacets.getDatasetKey());
    addIfPresent(facets, FIELD, esFacets.getField());
    addIfPresent(facets, ISSUE, esFacets.getIssue());
    addIfPresent(facets, NAME_ID, esFacets.getNameId());
    addIfPresent(facets, NAME_INDEX_ID, esFacets.getNameIndexId());
    addIfPresent(facets, NOM_STATUS, esFacets.getNomStatus());
    addIfPresent(facets, PUBLISHED_IN_ID, esFacets.getPublishedInId());
    addIfPresent(facets, RANK, esFacets.getRank());
    addIfPresent(facets, STATUS, esFacets.getStatus());
    addIfPresent(facets, TYPE, esFacets.getType());
    return facets;
  }

  private static void addIfPresent(Map<NameSearchParameter, Set<FacetValue<?>>> facets, NameSearchParameter param, EsFacet esFacet) {
    if (esFacet != null) {
      if (param.type() == String.class) {
        facets.put(param, createStringBuckets(esFacet));
      } else if (param.type() == Integer.class) {
        facets.put(param, createIntBuckets(esFacet));
      } else if (param.type().isEnum()) {
        facets.put(param, createEnumBuckets(esFacet, param));
      } else {
        throw new AssertionError("Unexpected parameter type: " + param.type());
      }
    }
  }

  private static Set<FacetValue<?>> createStringBuckets(EsFacet esFacet) {
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> FacetValue.forString(b.getKey(), b.getDocCount()))
        .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
  }

  private static Set<FacetValue<?>> createIntBuckets(EsFacet esFacet) {
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> FacetValue.forInteger(b.getKey(), b.getDocCount()))
        .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
  }

  private static <U extends Enum<U>> Set<FacetValue<?>> createEnumBuckets(EsFacet esFacet, NameSearchParameter param) {
    @SuppressWarnings("unchecked")
    Class<U> enumClass = (Class<U>) param.type();
    return esFacet.getBucketsContainer()
        .getBuckets()
        .stream()
        .map(b -> FacetValue.forEnum(enumClass, b.getKey(), b.getDocCount()))
        .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
  }

}