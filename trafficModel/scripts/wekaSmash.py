#!/usr/bin/env python3

import re
import makeASDataMine
import countryScalings

WEKA_RE = re.compile("\\S+\\s+\\S+\\s+(\\S+)\\s+[^\(]*\((.+)\)")
WEKA_RESULT_FILE = "../dataMiningResult.txt"

CC_UP_FILE = "../ccUpBW.csv"
CC_DOWN_FILE = "../ccDownBW.csv"

USEFUL_UP_FILE = "../coUpUseful.csv"
USEFUL_DOWN_FILE = "../coDownUseful.csv"

OUT_FILE = "../newWeights.txt"


def main():
    wekaResult = loadWekaResult()
    asToCountry = makeASDataMine.loadCountryMapping()
    upCC = loadCCResult(CC_UP_FILE)
    downCC = loadCCResult(CC_DOWN_FILE)
    usefulUp = loadUsefulFile(USEFUL_UP_FILE)
    usefulDown = loadUsefulFile(USEFUL_DOWN_FILE)
    countryToCont = countryScalings.buildCountryToContMapping()

    resultUp = wsmash(upCC, usefulUp, asToCountry, countryToCont, wekaResult)
    resultDown = wsmash(downCC, usefulDown, asToCountry,countryToCont, wekaResult)
    print("len of result " + str(len(resultUp)))
    print("len of weka " + str(len(wekaResult)))

    outFP = open(OUT_FILE, "w")
    for tAS in resultUp:
        outFP.write(tAS + "," + str(resultUp[tAS]) + "," + str(resultDown[tAS]) + "," + str(wekaResult[tAS]) + "\n")
    outFP.close()

def wsmash(ccMap, usefulMap, asToCountry, countryToCont, wekaResult):
    retMap = {}
    smashCountryMap = {}
    for tCC in ccMap:
        smashCountryMap[tCC] = ccMap[tCC] * usefulMap[countryToCont[tCC]]
    for tCC in smashCountryMap:
        totWeight = 0.0
        for tAS in asToCountry:
            if asToCountry[tAS] == tCC and tAS in wekaResult:
                totWeight = totWeight + wekaResult[tAS]
        for tAS in asToCountry:
            if asToCountry[tAS] == tCC and tAS in wekaResult:
                retMap[tAS] = wekaResult[tAS] / totWeight * smashCountryMap[tCC]
    return retMap

def loadUsefulFile(fileName):
    retMap = {}
    fp = open(fileName, "r")
    for line in fp:
        splits = line.split(",")
        if len(splits) == 2:
            retMap[splits[0]] = float(splits[1])
    fp.close()
    return retMap


def loadCCResult(fileName):
    retMap = {}
    fp = open(fileName, "r")
    for line in fp:
        splits = line.split(",")
        if len(splits) == 2:
            retMap[splits[0]] = float(splits[1])
    fp.close()
    return retMap

def loadWekaResult():
    retMap = {}
    fp = open(WEKA_RESULT_FILE, "r")
    inResultRegion = False
    for line in fp:
        if not inResultRegion and " inst#,    actual, predicted, error (ASN)" in line:
            inResultRegion = True
            print("found result region")
        elif inResultRegion:
            matcher = WEKA_RE.search(line.strip())
            if matcher:
                bwPredict = float(matcher.group(1))
                asn = matcher.group(2)
                retMap[asn] = bwPredict   
    fp.close()
    return retMap

if __name__ == "__main__":
    main()
