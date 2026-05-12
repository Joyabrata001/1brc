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

# Java env setup
Write-Host "--- Setting up env for the Powershell ---"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Clean build the project with Maven wrapper
Write-Host "--- Cleaning and building the project ---"
./mvnw clean verify
#./mvnw clean verify "-DskipTests" "-Dformat.skip" "-Dlicense.skip"    # skip format and license check

# Check if build successful befor running the app
if ($LASTEXITCODE -ne 0) {
    Write-Host  "Build failed! Please check the errors above."
    exit $LASTEXITCODE
}


Write-Host "--- Running the application ---"

$JAVA_OPTS = "" # For adding flags later on
# $JAVA_OPTS = "--enable-preview --add-modules jdk.incubator.vector"
java $JAVA_OPTS -cp "target/average-1.0.0-SNAPSHOT.jar" dev.morling.onebrc.CalculateAverage_joyab
