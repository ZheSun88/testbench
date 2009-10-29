cpvars=lib/selenium-server-1.0.1.jar:lib/selenium-grid-remote-control-standalone-TestBench-@build@.jar
host=192.168.56.101
huburl=http://192.168.56.101:4444
environment=*firefox
port=5555
userextensions=user-extensions.js

java -cp "$cpvars" com.thoughtworks.selenium.grid.remotecontrol.SelfRegisteringRemoteControlLauncher -host $host -port $port -hubUrl $huburl -env $environment -userExtensions $userextensions