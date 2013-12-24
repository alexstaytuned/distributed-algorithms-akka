name := "Broadcast Yo"
 
version := "1.0"
 
scalaVersion := "2.10.3"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Twitter Repository" at "http://artifactory.local.twitter.com/repo/"
 
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.2.3"