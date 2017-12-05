package org.col.db.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.col.TestEntityGenerator;
import org.col.api.Dataset;
import org.col.api.Name;
import org.col.api.NameSearch;
import org.col.api.Page;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 *
 */
public class NameMapperTest extends MapperTestBase<NameMapper> {

	public NameMapperTest() {
		super(NameMapper.class);
	}

	private static Name create(final String id, final Name basionym) throws Exception {
		Name n = TestEntityGenerator.newName(id);
		n.setBasionymKey(basionym.getKey());
		return n;
	}

	private static Name create(Dataset d) throws Exception {
		Name n = TestEntityGenerator.newName();
		n.setDatasetKey(d.getKey());
		return n;
	}

	@Test
	public void roundtrip() throws Exception {
		Name n1 = TestEntityGenerator.newName("sk1");
		mapper().create(n1);
		assertNotNull(n1.getKey());
		commit();

		int n1Key = mapper().lookupKey(TestEntityGenerator.DATASET1.getKey(), n1.getId());
		assertEquals((Integer) n1Key, n1.getKey());

		Name n1b = mapper().get(n1Key);
		assertEquals(n1, n1b);

		// now with basionym
		Name n2 = TestEntityGenerator.newName("sk2");
		n2.setBasionymKey(n1.getKey());
		mapper().create(n2);

		commit();

		// we use a new instance of n1 with just the keys for the equality tests
		// n1 = new Name();
		// n1.setKey(n2.getBasionymKey());
		// n1.setId(n2.getBasionymKey());
		// n2.setBasionymKey(n1);

		int n2Key = mapper().lookupKey(TestEntityGenerator.DATASET1.getKey(), n2.getId());
		assertEquals((Integer) n2Key, n2.getKey());
		Name n2b = mapper().get(n2Key);
		assertEquals(n2, n2b);
	}

	@Test
	public void list() throws Exception {
		List<Name> names = Lists.newArrayList();
		names.add(create(TestEntityGenerator.DATASET2));
		names.add(create(TestEntityGenerator.DATASET2));
		names.add(create(TestEntityGenerator.DATASET2));
		names.add(create(TestEntityGenerator.DATASET2));
		names.add(create(TestEntityGenerator.DATASET2));

		for (Name n : names) {
			mapper().create(n);
		}
		commit();

		// get first page
		Page p = new Page(0, 3);

		List<Name> res = mapper().list(TestEntityGenerator.DATASET2.getKey(), p);
		assertEquals(3, res.size());
		assertEquals(Lists.partition(names, 3).get(0), res);

		// next page
		p.next();
		res = mapper().list(TestEntityGenerator.DATASET2.getKey(), p);
		assertEquals(2, res.size());
		List<Name> l2 = Lists.partition(names, 3).get(1);
		assertEquals(l2, res);
	}

	@Test
	public void count() throws Exception {
		assertEquals(2, mapper().count(TestEntityGenerator.DATASET1.getKey()));

		mapper().create(TestEntityGenerator.newName());
		mapper().create(TestEntityGenerator.newName());
		commit();
		assertEquals(4, mapper().count(TestEntityGenerator.DATASET1.getKey()));
	}

	@Test
	public void basionymGroup() throws Exception {
		Name n2bas = TestEntityGenerator.newName("n2");
		mapper().create(n2bas);

		Name n1 = create("n1", n2bas);
		mapper().create(n1);

		Name n3 = create("n3", n2bas);
		mapper().create(n3);

		Name n4 = create("n4", n2bas);
		mapper().create(n4);

		commit();

		List<Name> s1 = mapper().basionymGroup(n1.getKey());
		assertEquals(4, s1.size());

		List<Name> s2 = mapper().basionymGroup(n2bas.getKey());
		assertEquals(4, s2.size());
		assertEquals(s1, s2);

		List<Name> s3 = mapper().basionymGroup(n3.getKey());
		assertEquals(4, s3.size());
		assertEquals(s1, s3);

		List<Name> s4 = mapper().basionymGroup(n4.getKey());
		assertEquals(4, s4.size());
		assertEquals(s1, s4);
	}

	@Test
	public void synonyms() throws Exception {
		final int accKey = TestEntityGenerator.TAXON1.getKey();
		final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();

		List<Name> synonyms = mapper().synonyms(accKey);
		assertTrue(synonyms.isEmpty());
		assertEquals(0, synonyms.size());

		// homotypic 1
		Name syn1 = TestEntityGenerator.newName("syn1");
		mapper().create(syn1);

		// homotypic 2
		Name syn2bas = TestEntityGenerator.newName("syn2bas");
		mapper().create(syn2bas);

		Name syn21 = TestEntityGenerator.newName("syn2.1");
		syn21.setBasionymKey(syn2bas.getKey());
		mapper().create(syn21);

		Name syn22 = TestEntityGenerator.newName("syn2.2");
		syn22.setBasionymKey(syn2bas.getKey());
		mapper().create(syn22);

		// homotypic 3
		Name syn3bas = TestEntityGenerator.newName("syn3bas");
		mapper().create(syn3bas);

		Name syn31 = TestEntityGenerator.newName("syn3.1");
		syn31.setBasionymKey(syn3bas.getKey());
		mapper().create(syn31);

		commit();

		// no synonym links added yet, expect empty synonymy even though basionym links
		// exist!
		synonyms = mapper().synonyms(accKey);
		assertTrue(synonyms.isEmpty());
		assertEquals(0, synonyms.size());

		// now add a few synonyms
		mapper().addSynonym(datasetKey, accKey, syn1.getKey());
		commit();
		synonyms = mapper().synonyms(accKey);
		assertFalse(synonyms.isEmpty());
		assertEquals(1, synonyms.size());

		mapper().addSynonym(datasetKey, accKey, syn2bas.getKey());
		mapper().addSynonym(datasetKey, accKey, syn21.getKey());
		mapper().addSynonym(datasetKey, accKey, syn22.getKey());
		mapper().addSynonym(datasetKey, accKey, syn3bas.getKey());
		mapper().addSynonym(datasetKey, accKey, syn31.getKey());

		synonyms = mapper().synonyms(accKey);
		assertEquals(6, synonyms.size());
	}

	@Test
	// Test with rank as extra search criterion
	public void searchWithRank() throws Exception {
		Name n = TestEntityGenerator.newName("a");
		n.setScientificName("Foo bar");
		n.setGenus("Foo");
		n.setSpecificEpithet("bar");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("b");
		n.setScientificName("Foo baz");
		n.setGenus("Foo");
		n.setSpecificEpithet("baz");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("c");
		n.setScientificName("Fee bar");
		n.setGenus("Fee");
		n.setSpecificEpithet("bar");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("d");
		n.setScientificName("Foo");
		n.setRank(Rank.PHYLUM);
		mapper().create(n);

		commit();

		NameSearch search = new NameSearch();
		search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		search.setQ("foo");
		List<Name> names = mapper().search(search, new Page());
		assertEquals("01", 3, names.size());

		search.setRank(Rank.SPECIES);
		names = mapper().search(search, new Page());
		assertEquals("02", 2, names.size());

		search.setQ("baz");
		names = mapper().search(search, new Page());
		assertEquals("03", 1, names.size());

		search.setQ("Foo");
		search.setRank(Rank.PHYLUM);
		names = mapper().search(search, new Page());
		assertEquals("04", 1, names.size());

		search.setRank(Rank.CLASS);
		names = mapper().search(search, new Page());
		assertEquals("05", 0, names.size());
}

	@Test
	// Test with status as extra search criterion
	public void searchWithTaxstatus() throws Exception {
		Name n = TestEntityGenerator.newName("a");
		n.setScientificName("Foo bar");
		n.setGenus("Foo");
		n.setSpecificEpithet("bar");
		n.setStatus(NomStatus.UNEVALUATED);
		mapper().create(n);

		n = TestEntityGenerator.newName("b");
		n.setScientificName("Foo baz");
		n.setGenus("Foo");
		n.setSpecificEpithet("baz");
		n.setStatus(NomStatus.UNEVALUATED);
		mapper().create(n);

		n = TestEntityGenerator.newName("c");
		n.setScientificName("Fee bar");
		n.setGenus("Fee");
		n.setSpecificEpithet("bar");
		n.setStatus(NomStatus.UNEVALUATED);
		mapper().create(n);

		n = TestEntityGenerator.newName("d");
		n.setScientificName("Foo");
		n.setStatus(NomStatus.CHRESONYM);
		mapper().create(n);

		commit();

		NameSearch search = new NameSearch();
		search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		search.setQ("foo");
		List<Name> names = mapper().search(search, new Page());
		assertEquals("01", 3, names.size());

		search.setNomstatus(NomStatus.UNEVALUATED);
		names = mapper().search(search, new Page());
		assertEquals("02", 2, names.size());

		search.setQ("baz");
		names = mapper().search(search, new Page());
		assertEquals("03", 1, names.size());

		search.setQ("Foo");
		search.setNomstatus(NomStatus.CHRESONYM);
		names = mapper().search(search, new Page());
		assertEquals("04", 1, names.size());
		
		search.setNomstatus(NomStatus.DOUBTFUL);
		names = mapper().search(search, new Page());
		assertEquals("05", 0, names.size());
	}

	@Test
	// Test with issue as extra search criterion
	public void searchWithIssue() throws Exception {
		
		Map<Issue, String> issue = new EnumMap<>(Issue.class);
		issue.put(Issue.UNPARSABLE_AUTHORSHIP, "Very unfortunate");

		Name n = TestEntityGenerator.newName("a");
		n.setScientificName("Foo bar");
		n.setGenus("Foo");
		n.setSpecificEpithet("bar");
		n.setIssues(issue);
		mapper().create(n);

		n = TestEntityGenerator.newName("b");
		n.setScientificName("Foo baz");
		n.setGenus("Foo");
		n.setSpecificEpithet("baz");
		n.setIssues(issue);
		mapper().create(n);

		n = TestEntityGenerator.newName("c");
		n.setScientificName("Fee bar");
		n.setGenus("Fee");
		n.setSpecificEpithet("bar");
		n.setIssues(issue);
		mapper().create(n);

		Map<Issue, String> otherIssue = new EnumMap<>(Issue.class);
		otherIssue.put(Issue.BIB_REFERENCE_INVALID, "Ouch");

		n = TestEntityGenerator.newName("d");
		n.setScientificName("Foo");
		n.setIssues(otherIssue);
		mapper().create(n);

		commit();		

		NameSearch search = new NameSearch();
		search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		search.setQ("foo");
		List<Name> names = mapper().search(search, new Page());
		assertEquals("01", 3, names.size());

		search.setIssue(Issue.UNPARSABLE_AUTHORSHIP);
		names = mapper().search(search, new Page());
		assertEquals("02", 2, names.size());

		search.setQ("baz");
		names = mapper().search(search, new Page());
		assertEquals("03", 1, names.size());

		search.setQ("Foo");
		search.setIssue(Issue.BIB_REFERENCE_INVALID);
		names = mapper().search(search, new Page());
		assertEquals("04", 1, names.size());
		
		search.setIssue(Issue.ALT_IDENTIFIER_INVALID);
		names = mapper().search(search, new Page());
		assertEquals("05", 0, names.size());
	}

	@Test
	public void countSearchResults() throws Exception {
		Name n = TestEntityGenerator.newName("a");
		n.setScientificName("Foo bar");
		n.setGenus("Foo");
		n.setSpecificEpithet("bar");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("b");
		n.setScientificName("Foo baz");
		n.setGenus("Foo");
		n.setSpecificEpithet("baz");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("c");
		n.setScientificName("Fee bar");
		n.setGenus("Fee");
		n.setSpecificEpithet("bar");
		n.setRank(Rank.SPECIES);
		mapper().create(n);

		n = TestEntityGenerator.newName("d");
		n.setScientificName("Foo");
		n.setRank(Rank.PHYLUM);
		mapper().create(n);

		commit();

		NameSearch search = new NameSearch();
		search.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		search.setQ("foo");
		int count = mapper().countSearchResults(search);
		assertEquals("01", 3, count);

		search.setRank(Rank.SPECIES);
		count = mapper().countSearchResults(search);
		assertEquals("02", 2, count);

		search.setQ("baz");
		count = mapper().countSearchResults(search);
		assertEquals("03", 1, count);

		search.setQ("Foo");
		search.setRank(Rank.PHYLUM);
		count = mapper().countSearchResults(search);
		assertEquals("04", 1, count);

		search.setRank(Rank.CLASS);
		count = mapper().countSearchResults(search);
		assertEquals("05", 0, count);
	}

}