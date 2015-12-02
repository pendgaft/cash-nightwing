#!/usr/bin/env python3

import re
import io

PEERING_BASE = "../peeringDB/rawdumps/dump"

ASNRE = re.compile("ClearDataTD\"\>([0-9]+)\&nbsp")
BWRE = re.compile("\>(.+)\&nbsp;")

def main():
    fullMap = {}
    for i in range(1, 465):
        tmpMap = parseFile(PEERING_BASE + str(i) + ".txt")
        for tASN in tmpMap:
            fullMap[tASN] = tmpMap[tASN]
    outFP = open("../peerDBResult.csv", "w")
    for tASN in fullMap:
        outFP.write(tASN + "," + fullMap[tASN] + "\n")
    outFP.close()


def parseFile(fileName):
    retMap = {}
    inFP = open(fileName, "r", encoding="latin-1")
    inRegion = 0
    curASN = None
    noASN = 0
    for line in inFP:
        if "participant_view.php?id=" in line:
            inRegion = 1
        elif inRegion == 1:
            matcher = ASNRE.search(line)
            if matcher:
                curASN = matcher.group(1)
                inRegion = 2
            else:
                noASN = noASN + 1
                inRegion = 0
        elif inRegion == 2:
            inRegion = 3
        elif inRegion == 3:
            matcher = BWRE.search(line)
            if matcher:
                retMap[curASN] = matcher.group(1)
            else:
                print("shiiiiiii")
            inRegion = 0
            curASN = None
    inFP.close()
    if noASN > 0:
        print("no asn " + str(noASN) + " in " + fileName)
    return retMap


if __name__ == "__main__":
    main()
