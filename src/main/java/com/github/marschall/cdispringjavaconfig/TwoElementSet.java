package com.github.marschall.cdispringjavaconfig;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Set;

final class TwoElementSet<E> implements Set<E> {

  private final E first;
  private final E second;
  
  TwoElementSet(E first, E second) {
    requireNonNull(first);
    requireNonNull(second);
    this.first = first;
    this.second = second;
  }

  @Override
  public int size() {
    return 2;
  }
  
  @Override
  public boolean contains(Object o) {
    return this.first.equals(o) || this.second.equals(o);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(this.first, this.second);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Set)) {
      return false;
    }
    Set<?> other = (Set<?>) obj;
    return other.size() == 2 && other.contains(this.first) && other.contains(this.second);
  }
  
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

}
