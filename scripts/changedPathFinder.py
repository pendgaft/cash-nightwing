#!/usr/bin/env python3

import argparse
import re
import io
import os
import numpy
import zlib

FIRST_ROUND_REGEX = "###([0-9]+),0"
TURN_REGEX = "\\*\\*\\*([0-9]+),2"
DATA_REGEX = "([0-9]+):([0-9]+), (.+)"



def main(wardenFileName, pathsFileName, outFileName):

    outFP = open(outFileName, "w")

    wardenSet = loadWardens(wardenFileName)

    basePaths = {}
    changedPaths = {}
    baseDirs = os.listdir(pathsFileName)
    for tFileName in baseDirs:
        fullPath = os.path.join(pathsFileName, tFileName, "path.log")
        if os.path.exists(fullPath):
            print("on " + fullPath)
            gogo(fullPath, outFP, wardenSet, basePaths, changedPaths)
    outFP.close()


def gogo(pathsFileName, outFP, wardenSet, basePath, changedPaths):
    filePtr = open(pathsFileName, "r")
    inFirstPass = False
    inRoundPass = False
    currentRound = None
    for line in filePtr:
        match = re.search(FIRST_ROUND_REGEX, line)
        if match and len(basePath) == 0:
            print("Turning on first pass.")
            inFirstPass = True
            continue
        match = re.search(TURN_REGEX, line)
        if match:
            inRoundPass = True
            currentRound = match.group(1)
            print("Turning on for round " + currentRound)
            continue
        match = re.search(DATA_REGEX, line)
        if match:
            src = match.group(1)
            dest = match.group(2)
            path = match.group(3)
            path = sanitizePathLying(path, dest, match.group(0))
            path = src + " " + path
            if inRoundPass:
                oldPath = basePath[buildPathKey(src, dest)]
                if (not oldPath == path) and (not path in changedPaths[buildPathKey(src, dest)]):
                    outFP.write(oldPath + "->" + path + "\n")
                    changedPaths[buildPathKey(src, dest)].add(path)
            elif inFirstPass:
                basePath[buildPathKey(src, dest)] = path
                changedPaths[buildPathKey(src, dest)] = set([])
        else:
            if inFirstPass:
                print("Turning OFF for first pass.")
                print("First pass total length: " + str(len(basePath)))
                inFirstPass = False
            if inRoundPass:
                print("Turning OFF for round " + currentRound)
                inRoundPass = False
                currentRound = None

def buildPathKey(src, dest):
    return str(src) + ":" + str(dest)

def sanitizePathLying(path, dest, fullLine):
    hops = path.split(" ")
    tail = -1
    for i in range(0, len(hops)):
        if hops[i] == dest:
            tail = i + 1
            break
    if tail == -1:
        return path
    if tail == len(hops):
        return path
    retStr = ""
    for i in range(0, tail):
        retStr += hops[i]
        if not i == tail - 1:
            retStr += " "
    return retStr

def loadWardens(wardenFileName):
    retSet = []
    filePtr = open(wardenFileName, "r")
    for line in filePtr:
        stripedLine = line.strip()
        if len(stripedLine) > 0:
            retSet.append(stripedLine)
    filePtr.close()
    return retSet

def outputPathLenDelta(filePtr, baseMap, basePath, currentMapMap, currentPathMap, currentRound):
    
    deltas = buildRoundPathLengthDelta(baseMap, basePath, currentMapMap, currentPathMap)
    average = numpy.average(deltas)
    median = numpy.median(deltas)
    stddev = numpy.std(deltas)
    filePtr.write(str(currentRound) + "," + str(average) + "," + str(median) + "," + str(stddev) + "," + str(len(deltas)) + "\n")

def buildRoundPathLengthDelta(baseMap, basePath, currentMap, currentPath):
    deltas = []
    noBase = 0
    for pathID in currentMap:
        if not pathID in baseMap:
            noBase += 1
            continue
        if not basePath[pathID] == currentPath[pathID]:
            deltas.append(currentMap[pathID] - baseMap[pathID])
    print("No base count " + str(noBase))
    return deltas

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-w", "--warden", help="Warden file", required=True)
    parser.add_argument("-p", "--paths", help="Paths file", required=True)
    parser.add_argument("-o", "--out", help="Out file base", required=True)
    args = parser.parse_args()
    
    main(args.warden, args.paths, args.out) 
