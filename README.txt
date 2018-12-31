K-Means in MapReduce
Homework 4 for CS6240
Fall 2018

Code author
-----------
Zexi Han

Installation
------------
These components are installed:
- JDK 1.8
- Hadoop 2.9.1
- Maven
- AWS CLI (for EMR execution)

Environment (macOS)
-----------
1) Example ~/.bash_profile:
# Hadoop
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home
export HADOOP_HOME=/usr/local/hadoop
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
export PATH=$PATH:$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin

# Scala
export SCALA_HOME=/usr/local/scala
export PATH=$PATH:$SCALA_HOME/bin

# Maven 
export MAVEN_HOME=/usr/local/maven
export PATH=$PATH:$MAVEN_HOME/bin

2) Explicitly set JAVA_HOME in $HADOOP_HOME/etc/hadoop/hadoop-env.sh:
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home

Execution
---------
All of the build & execution commands are organized in the Makefile.
1) Unzip project file.
2) Open command prompt.
3) Navigate to directory where project files unzipped.
4) Edit the Makefile to customize the environment at the top.
	Sufficient for standalone: hadoop.root, jar.name, local.input
	Other defaults acceptable for running standalone.
5) Standalone Hadoop:
	make switch-standalone		-- set standalone Hadoop environment (execute once)
	make local
6) Pseudo-Distributed Hadoop: (https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation)
	make switch-pseudo			-- set pseudo-clustered Hadoop environment (execute once)
	make pseudo					-- first execution
	make pseudoq				-- later executions since namenode and datanode already running 
7) AWS EMR Hadoop: (you must configure the emr.* config parameters at top of Makefile)
	make upload-input-aws		-- only before first execution
	make aws					-- check for successful execution with web interface (aws.amazon.com)
	make download-output-aws    -- after successful execution & termination
