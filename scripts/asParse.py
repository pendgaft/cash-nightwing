import argparse
import io
import os
import re


AS_PATTERN = "[a-zA-Z]+\\|([A-Z]{2})\\|asn\\|([0-9]+)\\|"
MAPPING_DIR = "../asnAllocations/"
OUT_DIR = "../countryMappings/"

def main(countryCode):
    resultsMap = {}
    dataFiles = os.listdir(MAPPING_DIR)
    for filename in dataFiles:
        filePtr = open(MAPPING_DIR + filename, "r")
        for line in filePtr:
            matcher = re.search(AS_PATTERN, line)
            if matcher:
                if not matcher.group(2) in resultsMap:
                    resultsMap[matcher.group(2)] = []
                resultsMap[matcher.group(2)].append(matcher.group(1))
        filePtr.close()
    filteredList = buildMatchingList(countryCode, resultsMap)
    filePtr = open(OUT_DIR + countryCode + "-as.txt", "w")
    for asn in filteredList:
        filePtr.write(asn + "\n")
    filePtr.close()

def buildMatchingList(cc, mapping):
    winning = []
    dup = 0
    for asn in mapping:
        if cc in mapping[asn]:
            if len(mapping[asn]) > 1:
                dup += 1
            else:
                winning.append(asn)
    print("rejected because of double " + str(dup))
    print("accepted " + str(len(winning)))
    return winning


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--country", help="country code to extract", required=True)
    args = parser.parse_args()

    main(args.country)
