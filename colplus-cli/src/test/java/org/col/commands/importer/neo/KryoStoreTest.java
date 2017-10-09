package org.col.commands.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import org.col.api.Page;
import org.col.api.Reference;
import org.gbif.utils.text.StringUtils;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class KryoStoreTest {

  @Test
  public void storeReferences() throws Exception {

    KryoPool pool = new KryoPool.Builder(new org.col.commands.importer.neo.kryo.NeoKryoFactory())
        .softReferences()
        .build();

    try (KryoStore<Page> store = new KryoStore(Page.class, File.createTempFile("kryo-",".bin"), pool)) {

      for (int i = 0; i < 100; i++) {
        Page r = buildPage();
        r.setOffset(i);
        store.add(r);
      }

      int counter = 0;
      for (Page r : store) {
        System.out.println(r);
        assertNotNull(r);
        assertEquals(counter, r.getOffset());
        counter++;
      }

      assertEquals(counter, 100);
    }
  }

  private Page buildPage() {
    return new Page(10, 12);
  }

  private Reference buildRef() {
    Reference r = Reference.create();
    r.setTitle("Harry Belafonte");
    r.setYear(1989);
    r.setId(StringUtils.randomString(12));
    return r;
  }

}