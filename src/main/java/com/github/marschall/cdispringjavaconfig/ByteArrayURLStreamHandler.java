package com.github.marschall.cdispringjavaconfig;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;


class ByteArrayURLStreamHandler extends URLStreamHandler {

  private byte[] data;

  ByteArrayURLStreamHandler(byte[] data) {
    this.data = data;
  }

  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new ByteArrayURLConnection(u, this.data);
  }

}
