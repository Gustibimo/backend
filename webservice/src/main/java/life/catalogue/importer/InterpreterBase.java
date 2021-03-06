package life.catalogue.importer;

import com.google.common.collect.Lists;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.parser.SafeParser.parse;

/**
 * Base interpreter providing common methods for both ACEF and DWC
 */
public class InterpreterBase {
  
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterBase.class);
  protected static final Pattern AREA_VALUE_PATTERN = Pattern.compile("[\\w\\s:.-]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final int MIN_YEAR = 1500;
  private static final int MAX_YEAR = Year.now().getValue() + 10;
  private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{3,4})\\s*(\\?)?(?!\\d)");
  
  protected final NeoDb store;
  protected final DatasetSettings settings;
  private final Gazetteer distributionStandard;
  protected final ReferenceFactory refFactory;

  public InterpreterBase(DatasetSettings settings, ReferenceFactory refFactory, NeoDb store) {
    this.settings = settings;
    this.refFactory = refFactory;
    this.store = store;
    if (settings.has(Setting.DISTRIBUTION_GAZETTEER)) {
      distributionStandard = settings.getEnum(Setting.DISTRIBUTION_GAZETTEER);
      LOG.info("Dataset wide distribution standard {} found in settings", distributionStandard);
    } else {
      LOG.info("No dataset wide distribution standard found in settings");
      distributionStandard = null;
    }
  }
  
  protected boolean requireTerm(VerbatimRecord v, Term term, Issue notExistingIssue){
    if (!v.hasTerm(term)) {
      v.addIssue(notExistingIssue);
      return false;
    }
    return true;
  }
  
  protected Reference setReference(VerbatimRecord v, Term refIdTerm, Consumer<String> refIdConsumer){
    Reference ref = null;
    if (v.hasTerm(refIdTerm)) {
      String rid = v.getRaw(refIdTerm);
      ref = refFactory.find(rid, null);
      if (ref == null) {
        LOG.debug("ReferenceID {} not existing but referred from {} in file {} line {}", rid, refIdTerm.prefixedName(), v.getFile(), v.fileLine());
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      } else {
        refIdConsumer.accept(ref.getId());
      }
    }
    return ref;
  }

  protected List<VernacularName> interpretVernacular(VerbatimRecord rec, BiConsumer<VernacularName, VerbatimRecord> addReference,
                                                     Term name, Term translit, Term lang, Term sex, Term area, Term... countryTerms) {
    String vname = rec.get(name);
    if (vname != null) {
      VernacularName vn = new VernacularName();
      vn.setVerbatimKey(rec.getId());
      vn.setName(vname);
      
      if (translit != null) {
        vn.setLatin(rec.get(translit));
      }
      if (lang != null) {
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(lang)).orNull(Issue.VERNACULAR_LANGUAGE_INVALID, rec));
      }
      if (sex != null) {
        vn.setSex(SafeParser.parse(SexParser.PARSER, rec.get(sex)).orNull(Issue.VERNACULAR_SEX_INVALID, rec));
      }
      if (area != null) {
        vn.setArea(rec.get(area));
      }
      vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(countryTerms)).orNull(Issue.VERNACULAR_COUNTRY_INVALID, rec));
  
      addReference.accept(vn, rec);
  
      return Lists.newArrayList(vn);
    }
    return Collections.emptyList();
  }
  
  protected List<Distribution> interpretDistribution(VerbatimRecord rec, BiConsumer<Distribution, VerbatimRecord> addReference,
                                                     Term tArea, Term tGazetteer, Term tStatus) {
    // require location
    if (rec.hasTerm(tArea)) {
      // which standard?
      Gazetteer gazetteer;
      if (distributionStandard != null) {
        gazetteer = distributionStandard;
      } else {
        gazetteer = parse(GazetteerParser.PARSER, rec.get(tGazetteer))
            .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, rec);
      }
      return createDistributions(gazetteer, rec.get(tArea), rec.get(tStatus), rec, addReference);
    }
    return Collections.emptyList();
  }
  
  private static Distribution createDistribution(VerbatimRecord rec, Gazetteer standard, String area, DistributionStatus status,
                                          BiConsumer<Distribution, VerbatimRecord> addReference) {
    Distribution d = new Distribution();
    d.setVerbatimKey(rec.getId());
    d.setGazetteer(standard);
    d.setArea(area);
    d.setStatus(status);
    addReference.accept(d, rec);
    return d;
  }
  
  protected static List<Distribution> createDistributions(@Nullable Gazetteer standard, final String locRaw, String statusRaw, VerbatimRecord rec,
                                                   BiConsumer<Distribution, VerbatimRecord> addReference) {
    if (locRaw != null) {

      final DistributionStatus status = parse(DistributionStatusParser.PARSER, statusRaw)
          .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, rec);

      if (standard == Gazetteer.TEXT) {
        return Lists.newArrayList( createDistribution(rec, Gazetteer.TEXT, locRaw, status, addReference) );
      
      } else {
        List<Distribution> distributions = new ArrayList<>();
        for (String loc : words(locRaw)) {
          // add gazetteer prefix if not yet included
          if (standard != null && loc.indexOf(':') < 0) {
            loc = standard.locationID(loc);
          }
          AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull(Issue.DISTRIBUTION_AREA_INVALID, rec);
          if (area != null) {
            // check if we have contradicting extracted a gazetteer
            if (standard != null && area.standard != Gazetteer.TEXT && area.standard != standard) {
              LOG.info("Area standard {} found in area {} different from explicitly given standard {} for {}",
                        area.standard, area.area, standard, rec);
            }
            distributions.add(createDistribution(rec, area.standard, area.area, status, addReference));
          }
        }
        return distributions;
      }
    }
    return Collections.emptyList();
  }

  private static List<String> words(String x) {
    if (x == null) return Collections.EMPTY_LIST;
    Matcher m = AREA_VALUE_PATTERN.matcher(x);
    List<String> words = new ArrayList<>();
    while (m.find()) {
      words.add(m.group(0));
    }
    return words;
  }

  protected List<Description> interpretDescription(VerbatimRecord rec, BiConsumer<Description, VerbatimRecord> addReference,
                                                   Term description, Term category, Term format, Term lang) {
    // require non empty description
    if (rec.hasTerm(description)) {
      Description d = new Description();
      d.setVerbatimKey(rec.getId());
      d.setCategory(rec.get(category));
      d.setFormat(SafeParser.parse(TextFormatParser.PARSER, rec.get(format)).orNull());
      d.setDescription(rec.get(description));
      d.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(lang)).orNull());
  
      addReference.accept(d, rec);
  
      return Lists.newArrayList(d);
    }
    return Collections.emptyList();
  }
  
  protected List<Media> interpretMedia(VerbatimRecord rec, BiConsumer<Media, VerbatimRecord> addReference,
                 Term type, Term url, Term link, Term license, Term creator, Term created, Term title, Term format) {
    // require media or link url
    if (rec.hasTerm(url) || rec.hasTerm(link)) {
      Media m = new Media();
      m.setVerbatimKey(rec.getId());
      m.setUrl( uri(rec, Issue.URL_INVALID, url));
      m.setLink( uri(rec, Issue.URL_INVALID, link));
      m.setLicense( SafeParser.parse(LicenseParser.PARSER, rec.get(license)).orNull() );
      m.setCapturedBy(rec.get(creator));
      m.setCaptured( date(rec, Issue.MEDIA_CREATED_DATE_INVALID, created) );
      m.setTitle(rec.get(title));
      m.setFormat(MediaInterpreter.parseMimeType(rec.get(format)));
      m.setType( SafeParser.parse(MediaTypeParser.PARSER, rec.get(type)).orNull() );
      MediaInterpreter.detectType(m);
      
      addReference.accept(m, rec);
  
      return Lists.newArrayList(m);
    }
    return Collections.emptyList();
  }

  protected LocalDate date(VerbatimRecord v, Issue invalidIssue, Term term) {
    FuzzyDate fd = fuzzydate(v, invalidIssue, term);
    return fd == null ? null : fd.toLocalDate();
  }

  protected FuzzyDate fuzzydate(VerbatimRecord v, Issue invalidIssue, Term term) {
    Optional<FuzzyDate> date;
    try {
      date = DateParser.PARSER.parse(v.get(term));
    } catch (UnparsableException e) {
      v.addIssue(invalidIssue);
      return null;
    }
    if (date.isPresent()) {
      if (date.get().isFuzzyDate()) {
        v.addIssue(Issue.PARTIAL_DATE);
      }
      return date.get();
    }
    return null;
  }

  protected URI uri(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return parse(UriParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Integer integer(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return SafeParser.parse(IntegerParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Double decimal(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return SafeParser.parse(DecimalParser.PARSER, v.getFirstRaw(terms)).orNull(invalidIssue, v);
  }

  protected Boolean bool(VerbatimRecord v, Issue invalidIssue, Term... terms) {
    return parse(BooleanParser.PARSER, v.getFirst(terms)).orNull(invalidIssue, v);
  }
  
  protected Boolean bool(VerbatimRecord v, Term... terms) {
    return parse(BooleanParser.PARSER, v.getFirst(terms)).orNull();
  }

  private static boolean hasNoSpace(String x) {
    return x == null || !x.contains(" ");
  }
  
  private static String lowercaseEpithet(String epithet, IssueContainer issues) {
    if (epithet != null) {
      if (epithet.trim().contains(" ")) {
        issues.addIssue(Issue.MULTI_WORD_EPITHET);
        
      } else if (!epithet.equals(epithet.toLowerCase())) {
        issues.addIssue(Issue.UPPERCASE_EPITHET);
        return epithet.toLowerCase();
      }
    }
    return epithet;
  }
  
  private static void setDefaultNameType(Name n) {
    if (n.getCode() == NomCode.VIRUS) {
      n.setType(NameType.VIRUS);
    } else {
      n.setType(NameType.SCIENTIFIC);
    }
  }

  public Optional<NameAccordingTo> interpretName(final boolean preferAtoms, final String id, final String vrank, final String sciname, final String authorship,
                                                 final String genus, final String infraGenus, final String species, final String infraspecies,
                                                 final String cultivar,final String phrase,
                                                 String nomCode, String nomStatus,
                                                 String link, String remarks, VerbatimRecord v) {
    // this can be wrong in some cases, e.g. in DwC records often scientificName and just a genus is given
    final boolean isAtomized = ObjectUtils.anyNotNull(genus, infraGenus, species, infraspecies);
    final boolean useAtoms   = isAtomized && (preferAtoms || sciname == null);

    // parse rank & code as they improve name parsing
    Rank rank = SafeParser.parse(RankParser.PARSER, vrank).orElse(Rank.UNRANKED, Issue.RANK_INVALID, v);
    final NomCode code = SafeParser.parse(NomCodeParser.PARSER, nomCode).orElse(settings.getEnum(Setting.NOMENCLATURAL_CODE), Issue.NOMENCLATURAL_CODE_INVALID, v);

    NameAccordingTo nat;

    // we can get the scientific name in various ways.
    // we prefer already atomized names as we want to trust humans more than machines
    if (useAtoms) {
      nat = new NameAccordingTo();
      Name atom = new Name();
      nat.setName(atom);

      atom.setGenus(genus);
      atom.setInfragenericEpithet(infraGenus);
      atom.setSpecificEpithet(lowercaseEpithet(species, v));
      atom.setInfraspecificEpithet(lowercaseEpithet(infraspecies, v));
      atom.setCultivarEpithet(cultivar);
      atom.setAppendedPhrase(phrase);
      atom.setRank(rank);
      atom.setCode(code);
      setDefaultNameType(atom);
      // populate uninomial instead of genus?
      if (!atom.isBinomial() && rank.isGenusOrSuprageneric() && atom.getGenus() != null && atom.getInfragenericEpithet() == null) {
        atom.setUninomial(atom.getGenus());
        atom.setGenus(null);
      }

      if (rank.otherOrUnranked()) {
        atom.setRank(RankUtils.inferRank(atom));
      }

      // parse the reconstructed name without authorship to detect name type and potential problems
      Optional<NameAccordingTo> natFromAtom = NameParser.PARSER.parse(atom.canonicalNameComplete(), rank, code, v);
      if (natFromAtom.isPresent()) {
        final Name pn = natFromAtom.get().getName();

        // check name type if its parsable - otherwise we should not use name atoms
        if (!pn.getType().isParsable()) {
          LOG.info("Atomized name {} appears to be of type {}. Use scientific name only", atom.canonicalNameComplete(), pn.getType());
          nat.setName(pn);
        } else if (pn.isParsed()) {
          // if parsed compare with original atoms
          if (
              !Objects.equals(atom.getUninomial(), pn.getUninomial()) ||
                  !Objects.equals(atom.getGenus(), pn.getGenus()) ||
                  !Objects.equals(atom.getInfragenericEpithet(), pn.getInfragenericEpithet()) ||
                  !Objects.equals(atom.getSpecificEpithet(), pn.getSpecificEpithet()) ||
                  !Objects.equals(atom.getInfraspecificEpithet(), pn.getInfraspecificEpithet())
          ) {
            LOG.warn("Parsed and given name atoms differ: [{}] vs [{}]", pn.canonicalNameComplete(), atom.canonicalNameComplete());
            v.addIssue(Issue.PARSED_NAME_DIFFERS);
          }
        }
      } else {
        // only really happens for blank strings
        LOG.info("No name given for {}", id);
        return Optional.empty();
      }

    } else if (sciname != null) {
      nat = NameParser.PARSER.parse(sciname, rank, code, v).get();

    } else {
      LOG.info("No name given for {}", id);
      return Optional.empty();
    }

    // try to add an authorship if not yet there
    NameParser.PARSER.parseAuthorshipIntoName(nat, authorship, v);

    // common basics
    nat.getName().setId(id);
    nat.getName().setVerbatimKey(v.getId());
    nat.getName().setOrigin(Origin.SOURCE);
    nat.getName().setLink(parse(UriParser.PARSER, link).orNull());
    // name status can be explicitly given or as part of the name remarks
    nat.getName().setNomStatus(parse(NomStatusParser.PARSER, nomStatus).orElse(
        parse(NomStatusParser.PARSER, nat.getName().getRemarks()).orNull(), Issue.NOMENCLATURAL_STATUS_INVALID, v)
    );
    // applies default dataset code if we cannot find or parse any
    // Always make sure this happens BEFORE we update the canonical scientific name
    nat.getName().setCode(code);
    // we add only to already parsed remarks
    nat.getName().addRemark(remarks);
    nat.getName().addRemark(nomStatus);

    // assign best rank
    if (rank.notOtherOrUnranked() || nat.getName().getRank() == null) {
      // TODO: check ACEF ranks...
      nat.getName().setRank(rank);
    }

    // finally update the scientificName with the canonical form if we can
    nat.getName().updateNameCache();

    return Optional.of(nat);
  }

  protected void setLifezones(Taxon t, VerbatimRecord v, Term lifezone) {
    String raw = v.get(lifezone);
    if (raw != null) {
      for (String lzv : words(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, v);
        if (lz != null) {
          t.getLifezones().add(lz);
        }
      }
    }
  }
  
  protected static Integer parseYear(Term term, VerbatimRecord v) {
    return parseYear(v.get(term), v);
  }
  
  protected static Integer parseYear(String year, IssueContainer issues) {
    if (!StringUtils.isBlank(year)) {
      Matcher m = YEAR_PATTERN.matcher(year.trim());
      if (m.find()) {
        Integer y;
        if (m.group(2) != null) {
          // convert ? to a zero
          y = Integer.parseInt(m.group(1)+"0");
        } else {
          y = Integer.parseInt(m.group(1));
        }
        if (y < MIN_YEAR || y > MAX_YEAR) {
          issues.addIssue(Issue.UNLIKELY_YEAR);
        }
        return y;
      
      } else {
        issues.addIssue(Issue.UNPARSABLE_YEAR);
      }
    }
    return null;
  }

}
