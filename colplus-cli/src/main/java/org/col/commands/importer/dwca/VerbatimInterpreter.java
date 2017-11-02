package org.col.commands.importer.dwca;

import org.col.api.*;
import org.col.api.exception.InvalidNameException;
import org.col.api.vocab.*;
import org.col.commands.importer.neo.InsertMetadata;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class VerbatimInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimInterpreter.class);

  private InsertMetadata insertMetadata;

  public VerbatimInterpreter(InsertMetadata insertMetadata) {
    this.insertMetadata = insertMetadata;
  }

  private static String first(VerbatimRecord v, Term... terms) {
    for (Term t : terms) {
      // verbatim data is cleaned already and all empty strings are removed from the terms map
      if (v.hasCoreTerm(t)) {
        return v.getCoreTerm(t);
      }
    }
    return null;
  }

  public NeoTaxon interpret(VerbatimRecord v, boolean useCoreIdForTaxonID) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getCoreTerm(dwc));
    }
    // add taxon in any case - we can swap stawtus of a synonym during normalization
    t.taxon = interpretTaxon(v, useCoreIdForTaxonID);
    // a synonym by status?
    // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during main normalization
    if(SafeParser.parse(SynonymStatusParser.PARSER, v.getCoreTerm(DwcTerm.taxonomicStatus)).orElse(false)) {
      t.synonym = new NeoTaxon.Synonym();
    }

    return t;
  }

  private Taxon interpretTaxon(VerbatimRecord v, boolean useCoreIdForTaxonID) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setId(useCoreIdForTaxonID ? v.getId() : v.getCoreTerm(DwcTerm.taxonID));
    t.setStatus(SafeParser.parse(TaxonomicStatusParser.PARSER, v.getCoreTerm(DwcTerm.taxonomicStatus))
        .orElse(TaxonomicStatus.DOUBTFUL)
    );
    //TODO: interpret all of Taxon via new dwca extension
    t.setAccordingTo(null);
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.getCoreTerm(DcTerm.references)).orNull());
    t.setFossil(null);
    t.setRecent(null);
    //t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReference(null);
    t.setRemarks(v.getCoreTerm(DwcTerm.taxonRemarks));

    if (insertMetadata.isParentNameMapped()) {
      Taxon parent = new Taxon();
      //parent.setScientificName(v.getCoreTerm(DwcTerm.parentNameUsage));
      parent.setId(v.getCoreTerm(DwcTerm.parentNameUsageID));
      t.setParent(parent);
    }
    return t;
  }

  private Name interpretName(VerbatimRecord v) {
    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    Name n;
    if (v.hasCoreTerm(DwcTerm.scientificName)) {
      try {
        n = NameParserGNA.PARSER.parse(v.getCoreTerm(DwcTerm.scientificName)).get();
        // TODO: validate name against optional atomized terms!
      } catch (UnparsableException e) {
        n = buildNameFromVerbatimTerms(v);
        n.addIssue(Issue.UNPARSABLE_NAME);
      }

    } else {
      n = buildNameFromVerbatimTerms(v);
    }
    // parse rank
    final Rank rank = SafeParser.parse(RankParser.PARSER, v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank))
        .orElse(Rank.UNRANKED, Issue.RANK_INVALID, n.getIssues());
    n.setRank(rank);

    // try to add an authorship if not yet there
    if (v.hasCoreTerm(DwcTerm.scientificNameAuthorship)) {
      try {
        Authorship authorship = parseAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        if (n.hasAuthorship()) {
          // TODO: compare authorships and raise warning if different
        } else {
          n.setAuthorship(authorship);
        }

      } catch (UnparsableException e) {
        LOG.warn("Unparsable authorship {}", v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        n.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
      }
    }

    n.setId(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID));
    n.setOrigin(Origin.SOURCE);
    n.setSourceUrl(UriParser.PARSER.parse(v.getCoreTerm(DcTerm.references)).orElse(null));
    n.setStatus(SafeParser.parse(NomStatusParser.PARSER, v.getCoreTerm(DwcTerm.nomenclaturalStatus))
        .orElse(null, Issue.NOMENCLATURAL_STATUS_INVALID, n.getIssues())
    );
    n.setNomenclaturalCode(SafeParser.parse(NomCodeParser.PARSER, v.getCoreTerm(DwcTerm.nomenclaturalCode))
        .orElse(null, Issue.NOMENCLATURAL_CODE_INVALID, n.getIssues())
    );
    //TODO: should we also get these through an extension, e.g. species profile or a nomenclature extension?
    n.setRemarks(v.getCoreTerm(CoLTerm.nomenclaturalRemarks));
    n.setEtymology(v.getCoreTerm(CoLTerm.etymology));
    n.setFossil(null);

    // basionym is kept purely in neo4j

    if (!n.isConsistent()) {
      n.addIssue(Issue.INCONSISTENT_NAME);
      LOG.warn("Inconsistent name: {}", n);
    }

    return n;
  }

  private Name buildNameFromVerbatimTerms(VerbatimRecord v) {
    Name n = new Name();
    n.setGenus(v.getFirst(GbifTerm.genericName, DwcTerm.genus));
    n.setInfragenericEpithet(v.getCoreTerm(DwcTerm.subgenus));
    n.setSpecificEpithet(v.getCoreTerm(DwcTerm.specificEpithet));
    n.setInfraspecificEpithet(v.getCoreTerm(DwcTerm.infraspecificEpithet));
    n.setType(NameType.SCIENTIFIC);
    //TODO: detect named hybrids in epithets manually
    n.setNotho(null);
    try {
      n.setScientificName(n.buildScientificName());
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
    return n;
  }

  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  private Authorship parseAuthorship(String authorship) throws UnparsableException {
      Name auth = NameParserGNA.PARSER.parse("Abies alba "+authorship).get();
      return auth.getAuthorship();
  }
}