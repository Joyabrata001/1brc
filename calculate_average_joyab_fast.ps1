<#
  Copyright 2023 The original authors

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
#>

# 1. Java environment setup
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Incremental Compile (Only my class)
Write-Host "--- Compiling only my class ---" -ForegroundColor Yellow
javac -cp "target/classes" -d "target/classes" src/main/java/dev/morling/onebrc/CalculateAverage_joyab.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compile failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

## 3. Run the application
#Write-Host "--- Running implementation ---" -ForegroundColor Cyan
#
## Added performance flags often needed for 1BRC
#$JAVA_OPTS = ""
#
## Using 'target/classes' to see the newly compiled code immediately
#java $JAVA_OPTS -cp "target/classes" dev.morling.onebrc.CalculateAverage_joyab

# 3. Package into a single JAR
Write-Host "--- Packaging into JAR ---" -ForegroundColor Green
jar --create --file=CalculateAverage_joyab.jar --main-class=dev.morling.onebrc.CalculateAverage_joyab -C target/classes .

# 4. Run the application (using the JAR)
Write-Host "--- Running from JAR ---" -ForegroundColor Cyan
java -jar CalculateAverage_joyab.jar