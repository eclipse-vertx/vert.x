package org.nodex.groovy.core

import org.nodex.core.Nodex

class Nodex {

  static org.nodex.core.Nodex j_instance = org.nodex.core.Nodex.instance

  static def go(Closure closure) {
    j_instance.go(new java.lang.Runnable() {
      public void run() {
        closure.call()
      }
    })
  }

}
