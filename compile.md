javac -d bin -cp "libs/*" src/*.java
java -cp "bin;libs/*" FacebookCollector
java -cp "bin;libs/*" FacebookEncoder
java -cp "bin;libs/*" FacebookDataToPostgres
java -cp "bin;libs/*" WekaFacebookClassification
java -cp "bin;libs/*" FacebookPredictor