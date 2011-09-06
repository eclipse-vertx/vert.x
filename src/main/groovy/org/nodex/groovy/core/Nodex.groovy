package org.nodex.groovy.core

class Nodex {

  static def j_instance = org.nodex.java.core.Nodex.instance

  static def go(closure) {
    j_instance.go(new java.lang.Runnable() {
      public void run() {
        closure.call()
      }
    })
  }

}
