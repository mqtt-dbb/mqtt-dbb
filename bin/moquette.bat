@ECHO OFF
rem #
rem # Copyright (c) 2012-2015 Andrea Selva
rem #

echo "                                                                         "
echo "  ___  ___                       _   _        ___  ________ _____ _____  "
echo "  |  \/  |                      | | | |       |  \/  |  _  |_   _|_   _| "
echo "  | .  . | ___   __ _ _   _  ___| |_| |_ ___  | .  . | | | | | |   | |   "
echo "  | |\/| |/ _ \ / _\ | | | |/ _ \ __| __/ _ \ | |\/| | | | | | |   | |   "
echo "  | |  | | (_) | (_| | |_| |  __/ |_| ||  __/ | |  | \ \/' / | |   | |   "
echo "  \_|  |_/\___/ \__, |\__,_|\___|\__|\__\___| \_|  |_/\_/\_\ \_/   \_/   "
echo "                   | |                                                   "
echo "                   |_|                                                   "
echo "                                                                         "
echo "                                               version: 0.12.1           "

rem java -server -XX:+UseG1GC -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=500 -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime -XX:+PrintPromotionFailure -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=10M -XX:+HeapDumpOnOutOfMemoryError -Djava.awt.headless=true -Dlog4j.configuration=file:moquette-0.12.1\config\moquette-log.properties -Dmoquette.path=moquette-0.12.1 -cp classes;lib\* io.moquette.broker.Server %*
java -Djava.awt.headless=true -Dlog4j.configuration=file:moquette\config\moquette-log.properties -Dmoquette.path=moquette -cp classes;lib\*;moquette\lib\*;paho\resources io.moquette.broker.Server %*
rem java -Djava.awt.headless=true -Dmoquette.path=moquette -cp classes;lib\*;paho\resources io.moquette.broker.Server %*


