package com.github.marschall.cdispringjavaconfig;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
    assertThat(set, contains("first"));
    assertThat(set, contains("second"));
    assertThat(set, not(contains("second")));
  }

}
