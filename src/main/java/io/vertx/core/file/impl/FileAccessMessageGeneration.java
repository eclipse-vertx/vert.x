package io.vertx.core.file.impl;

class FileAccessMessageGeneration {
  public static String getFileAccessErrorMessage(String verb, String path) {
    return "Unable to " + verb + " file at path '" + path + "'";
  }

  public static String getFolderAccessErrorMessage(String verb, String path) {
    return "Unable to " + verb + " folder at path '" + path + "'";
  }

  public static String getFileCopyErrorMessage(String from, String to) {
    return getFileDualOperationErrorMessage("copy", from, to);
  }

  public static String getFileMoveErrorMessage(String from, String to) {
    return getFileDualOperationErrorMessage("move", from, to);
  }

  private static String getFileDualOperationErrorMessage(String verb, String from, String to) {
    return "Unable to " + verb + " file from '" + from + "' to '" + to + "'";
  }
}
