package life.catalogue.api.search;

import life.catalogue.api.model.Sector;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.QueryParam;
import java.time.LocalDate;
import java.util.Objects;

public class SectorSearchRequest {
  
  @QueryParam("id")
  private Integer id;

  @QueryParam("targetId")
  private String targetId;

  private Integer datasetKey;

  @QueryParam("subjectId")
  private String subjectId;

  @QueryParam("subjectDatasetKey")
  private Integer subjectDatasetKey;
  
  @QueryParam("rank")
  private Rank rank;

  @QueryParam("name")
  private String name;

  @QueryParam("lastSync")
  private LocalDate lastSync;

  @QueryParam("mode")
  private Sector.Mode mode;

  @QueryParam("modifiedBy")
  private Integer modifiedBy;

  @QueryParam("subject")
  private boolean subject = false;

  @QueryParam("broken")
  private boolean broken = false;
  
  public static SectorSearchRequest byCatalogue(int datasetKey){
    SectorSearchRequest req = new SectorSearchRequest();
    req.datasetKey = datasetKey;
    return req;
  }

  public static SectorSearchRequest byDataset(int datasetKey, int subjectDatasetKey){
    SectorSearchRequest req = byCatalogue(datasetKey);
    req.subjectDatasetKey = subjectDatasetKey;
    return req;
  }
  
  public Integer getId() {
    return id;
  }
  
  public void setId(Integer id) {
    this.id = id;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(String subjectId) {
    this.subjectId = subjectId;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }
  
  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }
  
  public Sector.Mode getMode() {
    return mode;
  }
  
  public void setMode(Sector.Mode mode) {
    this.mode = mode;
  }

  public Rank getRank() {
    return rank;
  }
  
  public void setRank(Rank rank) {
    this.rank = rank;
  }
  
  public Integer getModifiedBy() {
    return modifiedBy;
  }
  
  public void setModifiedBy(Integer modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public boolean isSubject() {
    return subject;
  }

  public void setSubject(boolean subject) {
    this.subject = subject;
  }

  public boolean isBroken() {
    return broken;
  }
  
  public void setBroken(boolean broken) {
    this.broken = broken;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDate getLastSync() {
    return lastSync;
  }

  public void setLastSync(LocalDate lastSync) {
    this.lastSync = lastSync;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SectorSearchRequest that = (SectorSearchRequest) o;
    return subject == that.subject &&
      broken == that.broken &&
      Objects.equals(id, that.id) &&
      Objects.equals(targetId, that.targetId) &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(subjectId, that.subjectId) &&
      Objects.equals(subjectDatasetKey, that.subjectDatasetKey) &&
      rank == that.rank &&
      Objects.equals(name, that.name) &&
      Objects.equals(lastSync, that.lastSync) &&
      mode == that.mode &&
      Objects.equals(modifiedBy, that.modifiedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, targetId, datasetKey, subjectId, subjectDatasetKey, rank, name, lastSync, mode, modifiedBy, subject, broken);
  }
}
