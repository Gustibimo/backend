package org.col.db.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.col.api.Page;
import org.col.api.Taxon;
import org.col.dao.DaoTestUtil;
import org.junit.Test;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<TaxonMapper> {

	public TaxonMapperTest() {
		super(TaxonMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		Taxon in = DaoTestUtil.newTaxon("t1");
		mapper().create(in);
		assertNotNull(in.getKey());
		commit();
		Taxon out = mapper().get(DaoTestUtil.DATASET1.getKey(), in.getId());
		assertTrue(in.equalsShallow(out));
	}

	@Test
	public void count() throws Exception {
		int i = mapper().count(DaoTestUtil.DATASET1.getKey());
		// Just to make sure we understand our environment
		// 2 Taxa pre-inserted through InitMybatisRule.squirrels()
		assertEquals(2, i);
		mapper().create(DaoTestUtil.newTaxon("t2"));
		mapper().create(DaoTestUtil.newTaxon("t3"));
		mapper().create(DaoTestUtil.newTaxon("t4"));
		assertEquals(5, mapper().count(DaoTestUtil.DATASET1.getKey()));
	}

	@Test
	public void list() throws Exception {
		List<Taxon> taxa = new ArrayList<>();
		taxa.add(DaoTestUtil.newTaxon("t1"));
		taxa.add(DaoTestUtil.newTaxon("t2"));
		taxa.add(DaoTestUtil.newTaxon("t3"));
		taxa.add(DaoTestUtil.newTaxon("t4"));
		taxa.add(DaoTestUtil.newTaxon("t5"));
		taxa.add(DaoTestUtil.newTaxon("t6"));
		taxa.add(DaoTestUtil.newTaxon("t7"));
		taxa.add(DaoTestUtil.newTaxon("t8"));
		taxa.add(DaoTestUtil.newTaxon("t9"));
		for(Taxon t : taxa) {
			mapper().create(t);
		}
		commit();

    // get first page
    Page p = new Page(0,3);

    List<Taxon> res = mapper().list(DaoTestUtil.DATASET1.getKey(), p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertTrue(DaoTestUtil.TAXON1.equalsShallow(res.get(0)));
    assertTrue(DaoTestUtil.TAXON2.equalsShallow(res.get(1)));
    assertTrue(taxa.get(0).equalsShallow(res.get(2)));
    
		p.next();
		res = mapper().list(DaoTestUtil.DATASET1.getKey(), p);
		assertEquals(3, res.size());
		assertTrue(taxa.get(1).equalsShallow(res.get(0)));
		assertTrue(taxa.get(2).equalsShallow(res.get(1)));
		assertTrue(taxa.get(3).equalsShallow(res.get(2)));

	}

}