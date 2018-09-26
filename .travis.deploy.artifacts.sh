# PROJECT_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=project.version -B | grep -v '\[')
PROJECT_VERSION="3.6.0-SNAPSHOT"
if [[ "$PROJECT_VERSION" =~ .*SNAPSHOT ]] && [[ "${TRAVIS_BRANCH}" =~ ^master$|^[0-9]+\.[0-9]+$ ]] && [[ "${TRAVIS_PULL_REQUEST}" = "false" ]];
then
  mvn deploy -DskipTests -B;
fi
