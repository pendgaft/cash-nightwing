#!/bin/env python3

import io

AS_FILE = "../realTopo/20150201.as-rel.txt"

def main(countryFile):
    nonEmptyCC = loadTopoFile(AS_FILE)
    country = loadCountry(countryFile)
    resultingSet = nonEmptyCC - country
    outFP = open("../countryMappings/standunitedVS-" + countryFile + ".txt", "w")
    for tAS in resultingSet:
        outFP.write(tAS + "\n")
    outFP.close()

def loadCountry(countryCode):
    countryFPName = "../countryMappings/" + countryCode + "-asInt.txt"
    retSet = set([])
    fp = open(countryFPName, "r")
    for countryLine in fp:
        tmp = countryLine.strip()
        if len(tmp) > 0:
            retSet.add(tmp)
    fp.close()
    return retSet
    
def loadTopoFile(topoFile):
    nonEmptyCCSet = set([])
    fp = open(topoFile, "r")
    for topoLine in fp:
        if topoLine[0] == "#":
            continue
        frags = topoLine.strip().split("|")
        if len(frags) == 3:
            if int(frags[2]) == -1:
                nonEmptyCCSet.add(frags[0])
            elif int(frags[2]) == 1:
                nonEmptyCCSet.add(frags[1])
    fp.close()
    return nonEmptyCCSet
    

if __name__ == "__main__":
    main("IR")
    main("CN")
    main("US")
