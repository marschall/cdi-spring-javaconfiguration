package com.github.marschall.cdispringjavaconfig;

import static java.util.Objects.requireNonNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class TwoElementSet<E> extends AbstractSet<E> {

  private final E first;
  private final E second;
  
  TwoElementSet(E first, E second) {
    requireNonNull(first);
    requireNonNull(second);
    if (first.equals(second)) {
      throw new IllegalArgumentException("must contain two not equal elements, contains only: " + first);
    }
    this.first = first;
    this.second = second;
  }

  @Override
  public int size() {
    return 2;
  }
  
  @Override
  public boolean isEmpty() {
    return false;
  }
  
  @Override
  public boolean contains(Object o) {
    return this.first.equals(o) || this.second.equals(o);
  }
  
  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterator<E> iterator() {
    return new TwoElementIterator();
  }
  
  final class TwoElementIterator implements Iterator<E> {
    
    private int index = 0;

    @Override
    public boolean hasNext() {
      return this.index < 2;
    }

    @Override
    public E next() {
      if (this.index > 1) {
        throw new NoSuchElementException();
      }
      this.index += 1;
      if (this.index == 1) {
        return first;
      } else {
        return second;
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

}
