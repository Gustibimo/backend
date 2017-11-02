package org.col.api.vocab;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue {
  /**
   * The name string could not be parsed.
   */
  UNPARSABLE_NAME,

  /**
   * The authorship string could not be parsed.
   */
  UNPARSABLE_AUTHORSHIP,

  /**
   * An parsed, but inconsistent name.
   * E.g. the rank of the name does not match the given name parts or suffices.
   */
  INCONSISTENT_NAME,

  /**
   * The name parts contain unusual characters.
   */
  UNUSUAL_CHARACTERS,

  /**
   * At least one epithet equals "null" or "none".
   */
  NULL_EPITHET,

  /**
   * dwc:nomenclaturalStatus could not be interpreted
   */
  NOMENCLATURAL_STATUS_INVALID,

  /**
   * dwc:nomenclaturalCode could not be interpreted
   */
  NOMENCLATURAL_CODE_INVALID,

  /**
   * A recombination with a basionym authorship which does not match the authorship of the linked basionym.
   */
  BASIONYM_AUTHOR_MISMATCH,

  /**
   * Record has a verbatim original name (basionym) which is not unique and refers to several records.
   */
  ORIGINAL_NAME_NOT_UNIQUE,

  /**
   * Record has a original name (basionym) relationship which was derived from name & authorship comparison, but did not exist explicitly in the data.
   * This should only be flagged in programmatically generated GBIF backbone usages.
   * GBIF backbone specific issue.
   */
  ORIGINAL_NAME_DERIVED,

  /**
   * There have been more than one accepted name in a homotypical basionym group of names.
   * GBIF backbone specific issue.
   */
  CONFLICTING_BASIONYM_COMBINATION,

  /**
   * A potential orthographic variant exists in the dataset.
   */
  POTENTIAL_ORTHOGRAPHIC_VARIANT,

  /**
   * A canonical homonym exists for this name in the dataset.
   */
  HOMONYM,

  /**
   * A bi/trinomial name published earlier than the parent genus was published.
   * This might indicate that the name should rather be a recombination.
   */
  PUBLISHED_BEFORE_GENUS,
  
  
  
  
  
  // TODO: TAXON ISSUES TO BE REVISED !!!


  /**
   * The value for dwc:parentNameUsageID could not be resolved.
   */
  PARENT_NAME_USAGE_ID_INVALID,

  /**
   * The value for dwc:acceptedNameUsageID could not be resolved.
   */
  ACCEPTED_NAME_USAGE_ID_INVALID,

  /**
   * The value for dwc:originalNameUsageID could not be resolved.
   */
  ORIGINAL_NAME_USAGE_ID_INVALID,

  /**
   * Synonym lacking an accepted name.
   */
  ACCEPTED_NAME_MISSING,

  /**
   * dwc:taxonRank could not be interpreted
   */
  RANK_INVALID,

  /**
   * dwc:taxonomicStatus could not be interpreted
   */
  TAXONOMIC_STATUS_INVALID,

  /**
   * If a synonym points to another synonym as its accepted taxon the chain is resolved.
   */
  CHAINED_SYNOYM,


  TAXONOMIC_STATUS_MISMATCH,

  /**
   * The child parent classification resulted into a cycle that needed to be resolved/cut.
   */
  PARENT_CYCLE,

  /**
   * The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks.
   */
  CLASSIFICATION_RANK_ORDER_INVALID,

  /**
   * The denormalized classification could not be applied to the name usage.
   * For example if the id based classification has no ranks.
   */
  CLASSIFICATION_NOT_APPLIED,

  /**
   * At least one vernacular name extension record attached to this name usage is invalid.
   */
  VERNACULAR_NAME_INVALID,

  /**
   * At least one description extension record attached to this name usage is invalid.
   */
  DESCRIPTION_INVALID,

  /**
   * At least one distribution extension record attached to this name usage is invalid.
   */
  DISTRIBUTION_INVALID,

  /**
   * At least one species profile extension record attached to this name usage is invalid.
   */
  SPECIES_PROFILE_INVALID,

  /**
   * At least one multimedia extension record attached to this name usage is invalid.
   * This covers multimedia coming in through various extensions including
   * Audubon core, Simple images or multimedia or EOL media.
   */
  MULTIMEDIA_INVALID,

  /**
   * At least one bibliographic reference extension record attached to this name usage is invalid.
   */
  BIB_REFERENCE_INVALID,

  /**
   * At least one alternative identifier extension record attached to this name usage is invalid.
   */
  ALT_IDENTIFIER_INVALID,

  /**
   * Name usage could not be matched to the GBIF backbone.
   */
  BACKBONE_MATCH_NONE,

  /**
   * Name usage could only be matched to the GBIF backbone using fuzzy matching.
   *
   * @deprecated because there should be no fuzzy matching being used anymore for matching checklist names
   */
  @Deprecated
  BACKBONE_MATCH_FUZZY,

  /**
   * A scientific name has been used to point to another record (synonym->accepted, combination->basionym) which is not unique and refers to several records.
   */
  NAME_NOT_UNIQUE,

  /**
   * Record has a verbatim parent name which is not unique and refers to several records.
   */
  PARENT_NAME_NOT_UNIQUE,

  /**
   * There were problems representing all name usage relationships,
   * i.e. the link to the parent, accepted and/or original name.
   * The interpreted record in ChecklistBank is lacking some of the original source relation.
   */
  RELATIONSHIP_MISSING,

  /**
   * The group (currently only genera are tested) are lacking any accepted species
   * GBIF backbone specific issue.
   */
  NO_SPECIES,

  /**
   * The (accepted) bi/trinomial name does not match the parent name and should be recombined into the parent genus/species.
   * For example the species Picea alba with a parent genus Abies is a mismatch and should be replaced by Abies alba.
   * GBIF backbone specific issue.
   */
  NAME_PARENT_MISMATCH,

  /**
   * A potential orthographic variant exists in the backbone.
   * GBIF backbone specific issue.
   */
  ORTHOGRAPHIC_VARIANT;

}