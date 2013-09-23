package com.github.marschall.cdispringjavaconfig;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;

public class TwoElementSetTest {

  @Test
  public void simpleStrings() {
    TwoElementSet<String> set = new TwoElementSet<>("first", "second");
    assertThat(set, hasSize(2));
    
    assertTrue(set.contains("first"));
    assertTrue(set.contains("second"));
    assertFalse(set.contains("third"));
    
    // uses iterator
    assertThat(set, hasItem("first"));
    assertThat(set, hasItem("second"));
    assertThat(set, not(hasItem("third")));
  }
  
  @Test
  public void iteratorRemove() {
    TwoElementSet<String> set = new TwoElementSet<>("first", "second");
    Iterator<String> iterator = set.iterator();
    try {
      iterator.remove();
      fail("iterator should not support remove");
    } catch (UnsupportedOperationException e) {
      // should reach here
    }
  }
  
  @Test
  public void iterator() {
    TwoElementSet<String> set = new TwoElementSet<>("first", "second");
    Iterator<String> iterator = set.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("first", iterator.next());
    
    assertTrue(iterator.hasNext());
    assertEquals("second", iterator.next());
    
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail("iterator should be at end");
    } catch (NoSuchElementException e) {
      // should reach here
    }
  }

}
