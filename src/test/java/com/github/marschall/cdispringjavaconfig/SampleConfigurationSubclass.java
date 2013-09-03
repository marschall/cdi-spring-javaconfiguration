package com.github.marschall.cdispringjavaconfig;

/**
 * Reverse engineer the required byte code for a dynamic subclass.
 */
public class SampleConfigurationSubclass extends SampleConfiguration {

  private Pojo pojo;

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized Pojo pojo() {
    // TODO only for singleton scope
    if (this.pojo == null) {
      this.pojo = super.pojo();
    }
    return this.pojo;
  }

}
