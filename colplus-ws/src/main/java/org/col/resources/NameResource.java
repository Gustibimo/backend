package org.col.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.NameDao;
import org.col.db.dao.NameUsageDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.dw.jersey.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/name")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class NameResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
  public ResultPage<Name> list(@QueryParam("datasetKey") Integer datasetKey,
      @Valid @BeanParam Page page, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.list(datasetKey, page);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsage> search(@BeanParam NameSearch query, @Valid @BeanParam Page page,
      @Context SqlSession session) {
    NameUsageDao dao = new NameUsageDao(session);
    return dao.search(query, page);
  }

  @GET
  @Path("{id}/{datasetKey}")
  public Integer lookupKey(@PathParam("id") String id, @PathParam("datasetKey") int datasetKey,
      @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    Integer key = dao.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Name.class, datasetKey, id);
    }
    return key;
  }

  @GET
  @Path("{key}")
  public Name get(@PathParam("key") int key, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    Name name = dao.get(key);
    if (name == null) {
      throw NotFoundException.keyNotFound(Name.class, key);
    }
    return name;
  }

  @GET
  @Path("{key}/synonyms")
  public List<Name> getSynonyms(@PathParam("key") int key, @Context SqlSession session) {
    NameDao dao = new NameDao(session);
    return dao.homotypicGroup(key);
  }

  @GET
  @Path("{key}/acts")
  public List<NameRelation> getActs(@PathParam("key") int key, @Context SqlSession session) {
    NameRelationMapper mapper = session.getMapper(NameRelationMapper.class);
    return mapper.list(key);
  }

  /**
   * TODO: this is really a names index / prov catalogue specific method. Move it to a dedicated web
   * resource
   */
  @GET
  @Path("{key}/group")
  public List<Name> getIndexGroup(@PathParam("key") int key, @Context SqlSession session) {
    return session.getMapper(NameMapper.class).indexGroup(key);
  }
}
