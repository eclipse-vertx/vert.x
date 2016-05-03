# Building Vert.x

### Prerequisites

* Maven 3.2.x
* JDK 1.8+

### Regular build

    mvn clean install
    
### To run tests

    mvn test
        
To run a specific test

    mvn test -Dtest=<test_name/pattern>        
    
### Coverage build
    
To collect code coverage data, you need to launch the build with:
    
    mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package -Pcoverage
    
### Imports in IDE
    
To import the sources in your IDE, just import the project as a Maven project. However, check that:
      
*  `src/main/generated` is marked as a source directory
*  `target/generated-test-sources/test-annotations` is marked as a test source directory
    
This second directory is generated during the `test-compile` phase:
    
    mvn test-compile
    
### To build a distro

See the Vert.x stack project: https://github.com/vert-x3/vertx-stack and more specifically the `stack-manager`.




