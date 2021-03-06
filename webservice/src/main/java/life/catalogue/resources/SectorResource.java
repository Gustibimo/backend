package life.catalogue.resources;

import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.DiffService;
import life.catalogue.db.tree.NamesDiff;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.stream.Stream;

@Path("/dataset/{datasetKey}/sector")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class SectorResource extends AbstractDatasetScopedResource<Integer, Sector, SectorSearchRequest> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorResource.class);
  private final SectorDao dao;
  private final TaxonDao tdao;
  private final DatasetImportDao diDao;
  private final DiffService diff;
  private final AssemblyCoordinator assembly;

  public SectorResource(SectorDao dao, TaxonDao tdao, DatasetImportDao diDao, DiffService diffService, AssemblyCoordinator assembly) {
    super(Sector.class, dao);
    this.dao = dao;
    this.diDao = diDao;
    this.tdao = tdao;
    this.diff = diffService;
    this.assembly = assembly;
  }

  @Override
  ResultPage<Sector> searchImpl(int datasetKey, SectorSearchRequest req, Page page) {
    if (req.isSubject()) {
      req.setSubjectDatasetKey(datasetKey);
    } else {
      req.setDatasetKey(datasetKey);
    }
    return dao.search(req, page);
  }

  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteByDataset(@QueryParam("datasetKey") int datasetKey,
                              @PathParam("datasetKey") int catalogueKey,
                              @Context SqlSession session, @Auth User user) {
    SectorMapper sm = session.getMapper(SectorMapper.class);
    int counter = 0;
    for (Sector s : sm.listByDataset(catalogueKey, datasetKey)) {
      assembly.deleteSector(s.getId(), user);
      counter++;
    }
    LOG.info("Scheduled deletion of all {} sectors for dataset {} in catalogue {}", counter, datasetKey, catalogueKey);
  }

  @GET
  @Path("sync")
  public ResultPage<SectorImport> list(@PathParam("datasetKey") int datasetKey,
                                       @QueryParam("sectorKey") Integer sectorKey,
                                       @QueryParam("datasetKey") Integer subjectDatasetKey,
                                       @QueryParam("state") List<SectorImport.State> states,
                                       @QueryParam("running") Boolean running,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    if (running != null) {
      states = running ? SectorImport.runningStates() : SectorImport.finishedStates();
    }
    final List<SectorImport.State> immutableStates = ImmutableList.copyOf(states);
    SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
    List<SectorImport> imports = sim.list(sectorKey, datasetKey, subjectDatasetKey, states, page);
    return new ResultPage<>(page, imports, () -> sim.count(sectorKey, datasetKey, subjectDatasetKey, immutableStates));
  }

  @POST
  @Path("sync")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("datasetKey") int datasetKey, RequestScope request, @Auth User user, @Context SqlSession session) {
    DaoUtils.requireManaged(datasetKey, session);
    assembly.sync(datasetKey, request, user);
  }

  @DELETE
  @Override
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") Integer id, @Auth User user) {
    // an asynchroneous sector deletion will be triggered which also removes catalogue data
    assembly.deleteSector(id, user);
  }

  @DELETE
  @Path("{id}/sync")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSync(@PathParam("datasetKey") int datasetKey, @PathParam("id") int id, @Auth User user, @Context SqlSession session) {
    DaoUtils.requireManaged(datasetKey, session);
    assembly.cancel(id, user);
  }

  @GET
  @Path("{id}/sync/{attempt}")
  public SectorImport getSyncAttempt(@PathParam("datasetKey") int datasetKey,
                                       @PathParam("id") int id,
                                       @PathParam("attempt") int attempt,
                                       @Context SqlSession session) {
    DaoUtils.requireManaged(datasetKey, session);
    return session.getMapper(SectorImportMapper.class).get(id, attempt);
  }

  @GET
  @Path("{id}/sync/{attempt}/tree")
  public Stream<String> getSyncAttemptTree(@PathParam("id") int id,
                                             @PathParam("attempt") int attempt) throws IOException {
    return diDao.getTreeDao().getSectorTree(id, attempt);
  }
  
  @GET
  @Path("{id}/sync/{attempt}/names")
  public Stream<String> getSyncAttemptNames(@PathParam("id") int id,
                                              @PathParam("attempt") int attempt) {
    return diDao.getTreeDao().getSectorNames(id, attempt);
  }
  
  @GET
  @Path("{id}/treediff")
  public Reader diffTree(@PathParam("id") int id,
                         @QueryParam("attempts") String attempts,
                         @Context SqlSession session) throws IOException {
    return diff.sectorTreeDiff(id, attempts);
  }
  
  @GET
  @Path("{id}/namesdiff")
  public NamesDiff diffNames(@PathParam("id") int id,
                             @QueryParam("attempts") String attempts,
                             @Context SqlSession session) throws IOException {
    return diff.sectorNamesDiff(id, attempts);
  }

  @POST
  @Path("count-update")
  public boolean updateAllSectorCounts(@PathParam("datasetKey") int datasetKey, @Context SqlSession session) {
    DaoUtils.requireManaged(datasetKey, session);
    tdao.updateAllSectorCounts(datasetKey);
    session.commit();
    return true;
  }

}
