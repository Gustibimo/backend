package life.catalogue.api.event;

import com.google.common.base.Preconditions;
import life.catalogue.api.model.DataEntity;

public class EntityChanged<K, T> {
  public final K key;
  public final T obj;
  public final Class<T> objClass;

  public static <K, T extends DataEntity<K>>  EntityChanged<K,T> change(T obj){
    return new EntityChanged<>(obj.getKey(), obj, (Class<T>) obj.getClass());
  }

  public static <K, T> EntityChanged<K, T> delete(K key, Class<T> objClass){
    return new EntityChanged<>(key, null, objClass);
  }

  EntityChanged(K key, T obj, Class<T> objClass) {
    this.key = Preconditions.checkNotNull(key);
    this.obj = obj; // can be null in case of deletions
    this.objClass = Preconditions.checkNotNull(objClass);
  }

  public boolean isDeletion(){
    return obj == null;
  }

}
