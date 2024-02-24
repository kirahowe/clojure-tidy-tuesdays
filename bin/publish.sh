set -euo pipefail

# Publish tidy tuesday book on the internet
#######

function echoOrange() {
  ORANGE='\033[38;5;208m'
  RESET='\033[0m'
  printf "${ORANGE}$1${RESET}"
}

# cd into the directory that this script is in
cd "$(dirname "$0")"

cd ..
echoOrange ">>>>>> Building quarto book with clay...\n"
# todo.. figure out how to call dev/build
clojure -X dev/build-cli

cd docs
echoOrange ">>>>>> Updating _quarto.yml...\n"
echo 'resources: "*/*.csv"' >> _quarto.yml

echoOrange ">>>>>> Publishing to gh-pages with quarto publish\n"
quarto publish gh-pages --no-prompt
