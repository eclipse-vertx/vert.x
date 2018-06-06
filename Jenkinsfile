library "vertx-ci-workflow@master"
pipeline {
  agent any
  tools {
    maven 'Maven'
  }
  environment {
    MAVEN_SETTINGS_PATH = credentials("jenkins-sonatype-snapshot-repo-settings")
  }
  stages {
    stage ('OracleJDK 8') {
      tools {
        jdk 'OracleJDK 8'
      }
      steps {
        sh 'mvn -U -B -Dsurefire.reportNameSuffix=OracleJDK_8 clean deploy -s $MAVEN_SETTINGS_PATH'
        triggerWorkflow()
      }
      post {
        always {
          junit '**/target/surefire-reports/*.xml'
        }
        failure {
          mail to:'vertx3-ci@googlegroups.com', subject:"Job '${env.JOB_NAME}' (${env.BUILD_NUMBER})", body: "Please go to ${env.BUILD_URL}."
        }
      }
    }
    stage ('OracleJDK latest') {
      tools {
        jdk 'OracleJDK latest'
      }
      when {
        branch 'master'
      }
      steps {
        sh 'mvn -U -B -fn -Dsurefire.reportNameSuffix=OracleJDK_latest clean test'
      }
      post {
        always {
          junit '**/target/surefire-reports/*.xml'
        }
      }
    }
  }
}
