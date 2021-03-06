package life.catalogue.resources;

import com.google.common.base.Preconditions;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.User;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{datasetKey}/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends AbstractDatasetScopedResource<Integer, EditorialDecision, DecisionSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  private final DecisionDao dao;
  
  public DecisionResource(DecisionDao ddao) {
    super(EditorialDecision.class, ddao);
    this.dao = ddao;
  }

  @Override
  ResultPage<EditorialDecision> searchImpl(int datasetKey, DecisionSearchRequest req, Page page) {
    if (req.isSubject()) {
      req.setSubjectDatasetKey(datasetKey);
    } else {
      req.setDatasetKey(datasetKey);
    }
    req.setDatasetKey(datasetKey);
    return dao.search(req, page);
  }

  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@PathParam("datasetKey") int catalogueKey,
                              @QueryParam("datasetKey") Integer datasetKey,
                              @Context SqlSession session, @Auth User user) {
    Preconditions.checkNotNull(datasetKey, "datasetKey parameter is required");
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    int counter = 0;
    for (EditorialDecision d : mapper.processDecisions(catalogueKey, datasetKey)) {
      dao.delete(d.getKey(), user.getKey());
      counter++;
    }
    LOG.info("Deleted {} decisions for dataset {} in catalogue {}", counter, datasetKey, catalogueKey);
  }
}
