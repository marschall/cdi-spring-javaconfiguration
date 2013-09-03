package com.github.marschall.cdispringjavaconfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;


class ByteArrayURLConnection extends URLConnection {

  private final byte[] data;

  ByteArrayURLConnection(URL url, byte[] data) {
    super(url);
    this.data = data;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(this.data);
  }

  @Override
  public int getContentLength() {
    return this.data.length;
  }

  @Override
  public long getContentLengthLong() {
    return this.data.length;
  }

  @Override
  public String getContentType() {
    return "application/octet-stream";
  }

  @Override
  public String getContentEncoding() {
    return null;
  }

  @Override
  public void connect() {
    // nothing
  }

}
