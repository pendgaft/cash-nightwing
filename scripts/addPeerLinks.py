#!/usr/bin/env python3

import io
import random

AS_REL_FILE = "../realTopo/20150201.as-rel.txt"

def main():
    custMap = {}
    peerMap = {}
    asMap = set([])
    
    fp = open(AS_REL_FILE, "r")
    for line in fp:
        line = line.strip()
        if len(line) == 0:
            continue
        if line[0:1] == "#":
            continue
        tokens = line.split("|")
        if len(tokens) == 3:
            lhs = int(tokens[0])
            rhs = int(tokens[1])
            rel = int(tokens[2])
            asMap.add(lhs)
            asMap.add(rhs)
            if rel == -1:
                if not lhs in custMap:
                    custMap[lhs] = set([])
                custMap[lhs].add(rhs)
            elif rel == 1:
                if not rhs in custMap:
                    custMap[rhs] = set([])
                custMap[rhs].add(lhs)
            elif rel == 0:
                if not lhs in peerMap:
                    peerMap[lhs] = set([])
                if not rhs in peerMap:
                    peerMap[rhs] = set([])
                peerMap[lhs].add(rhs)
                peerMap[rhs].add(lhs)
    fp.close()

    linkCountTotal = 0
    for tProv in custMap:
        linkCountTotal = linkCountTotal + len(custMap[tProv])
    for tPeer in peerMap:
        linkCountTotal = linkCountTotal + len(peerMap[tPeer])
    print("total edges " + str(linkCountTotal))

    asList = []
    for tAS in custMap:
        asList.append(tAS)
    useAS = len(asList)
    
    for i in range(1, 4):
        addedLinkMap = {}
        fract = float(i) / 10.0
        tmpCount = int(fract * linkCountTotal)
        for j in range(0, tmpCount):
            aPos = -1
            bPos = -1
            stillGen = True
            while stillGen:
                aPos = asList[random.randint(0, useAS - 1)]
                bPos = asList[random.randint(0, useAS - 1)]
                stillGen = False
                if bPos in custMap[aPos] or aPos in custMap[bPos]:
                    stillGen = True
                elif bPos in peerMap and aPos in peerMap[bPos]:
                    stillGen = True
                elif aPos in peerMap and bPos in peerMap[aPos]:
                    stillGen = True
                elif aPos in addedLinkMap and bPos in addedLinkMap[aPos]:
                    stillGen = True
                elif bPos in addedLinkMap and aPos in addedLinkMap[bPos]:
                    stillGen = True
            if not aPos in addedLinkMap:
                addedLinkMap[aPos] = set([])
            addedLinkMap[aPos].add(bPos)
        fp = open(AS_REL_FILE + "-" + str(i), "w")
        for tProv in custMap:
            for tCust in custMap[tProv]:
                fp.write(str(tProv) + "|" + str(tCust) + "|-1\n")
        for tPeer1 in peerMap:
            for tPeer2 in peerMap[tPeer1]:
                fp.write(str(tPeer1) + "|" + str(tPeer2) + "|0\n")
        for tPeer1 in addedLinkMap:
            for tPeer2 in addedLinkMap[tPeer1]:
                fp.write(str(tPeer1) + "|" + str(tPeer2) + "|0\n")
        fp.close()
            

if __name__ == "__main__":
    main()
