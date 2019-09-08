package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DisMaxQuery extends AbstractQuery<DisMaxConstraint> {

  @JsonProperty("dis_max")
  private final DisMaxConstraint constraint;

  public DisMaxQuery() {
    constraint = new DisMaxConstraint();
  }

  public DisMaxQuery subquery(Query query) {
    constraint.subquery(query);
    return this;
  }

  @Override
  DisMaxConstraint getConstraint() {
    return constraint;
  }

}
