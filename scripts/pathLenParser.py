#!/usr/bin/env python3

import os.path
import re
import hashlib
import argparse
import time

#TODO check if this import is correct
import numpy

PATHS_FILENAME = "path.log"
OUT_STUB = "path"

ROUND_HEADER_PAT = re.compile("[\\#\\#\\#|\\*\\*\\*](\\d+)\\,(\\d+)")
DATA_LINE_PAT = re.compile("(\\d+:\\d+), (.+)")

def buildResistorSet(resistorFileName):
    fileP = open(resistorFileName, "r")
    retSet = set([])
    for line in fileP:
        if len(line.strip()) > 0:
            retSet.add(int(line))
    fileP.close()
    return retSet


def parse(pathsInFileName, resultsOutFileBase, resistSet):
    pathsIn = open(pathsInFileName, "r")
    pathsFromResistorOut = open(resultsOutFileBase + "-fromResistor.csv", "w")
    pathsToResistorOut = open(resultsOutFileBase + "-toResistor.csv", "w")

    baseLen = {}
    toDelta = []
    fromDelta = []
    pathHash = {}

    currentSize = None
    
    firstRoundDone = False
    inFirstRound = False
    harvesting = False

    startTime = 0
    
    for line in pathsIn:
        matcher = ROUND_HEADER_PAT.search(line.strip())
        if matcher:
            if inFirstRound:
                inFirstRound = False
                firstRoundDone = True
                harvesting = False
                print("done with initial harvest, built " + str(len(pathHash)) + " paths")
                print("took " + str(time.time() - startTime))
            elif not firstRoundDone:
                inFirstRound = True
                harvesting = True
                print("starting first round harvest")
                startTime = time.time()
            else:
                round = int(matcher.group(2))
                if harvesting:
                    #TODO write to file and clear temp lens
                    #AVG, MED, STDDEV, COUNT
                    toMean = numpy.mean(toDelta)
                    toMedian = numpy.median(toDelta)
                    toStd = numpy.std(toDelta)
                    fromMean = numpy.mean(fromDelta)
                    fromMedian = numpy.median(fromDelta)
                    fromStd = numpy.std(fromDelta)

                    pathFromResistorOut.write(str(currentSize) + "," + fromMean + "," + fromStd + "," + fromMedian + "," str(len(fromDelta)) + "\n")
                    pathToResistorOut.write(str(currentSize) + "," + toMean + "," + toStd + "," + toMedian + "," + str(len(toDelta)) + "\n")
                    toDelta.clear()
                    fromDelta.clear()
                harvesting = (round == 2)
                currentSize = int(matcher.group(1))
            continue
        if harvesting:
            matcher = DATA_LINE_PAT.search(line.strip())
            if matcher:
                hasher = hashlib.md5()
                hasher.update(matcher.group(2).encode('utf-8'))
                hashVal = hasher.digest()
                myKey = matcher.group(1)
                if inFirstRound:
                    pathLen = len(matcher.group(2).split(" "))
                    baseLen[myKey] = pathLen
                    pathHash[myKey] = hashVal
                else:
                    if not pathHash[myKey] == hashVal:
                        tokens = matcher.group(1).split(":")
                        pathDelta = len(matcher.group(2).split(" ")) - baseLen[myKey]
                        if int(tokens[0]) in resistSet:
                            fromDelta.append(pathDelta)
                        if int(tokens[1]) in resistSet:
                            toDelta.append(pathDelta)
    
    pathsIn.close()
    pathsFromResistorOut.close()
    pathsToResistorOut.close()
    


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-b", "--base_file", required=True, help="folder holding data files")
    parser.add_argument("-r", "--resistor_file", required=True, help="resistor config file")
    args = parser.parse_args()

    pathsInFileName = os.path.join(args.base_file, PATHS_FILENAME)
    pathsOutFileStub = os.path.join(args.base_file, OUT_STUB)
    resistSet = buildResistorSet(args.resistor_file)
    parse(pathsInFileName, pathsOutFileStub, resistSet)
