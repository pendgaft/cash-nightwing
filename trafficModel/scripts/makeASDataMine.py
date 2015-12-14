#!/usr/bin/env python3

import os

COUNTRY_DIR = "../../countryMappings/"
IP_FILE = "../../realTopo/whole-internet-20150101-ip.txt"
AS_REL_FILE = "../../realTopo/20150201.as-rel.txt"
PEER_DB_FILE = "../peerDBResult.csv"

def main():
    asToCC = loadCountryMapping()
    asToIP = loadIPMapping()
    asToDeg = loadASDegree()
    asToCCSize = loadCustConeSize(None)
    asToIPCCSize = loadCustConeSize(asToIP)
    peerDBMap = loadPeerMap()

    outFP = open("../dataMineKnown.csv", "w")
    lost = 0
    found = 0
    outFP.write("ASN,Country,IP Size,Degree,CC Size (AS),CC Size, (IP),Speed\n")
    for tAS in peerDBMap:
        if not peerDBMap[tAS] == "Not Disclosed\n":
            if tAS in asToIP and tAS in asToDeg and tAS in asToCCSize:
                cc = "XX"
                if tAS in asToCC:
                    cc = asToCC[tAS]
                outFP.write(tAS + "," + cc + "," + str(asToIP[tAS]) + "," + str(asToDeg[tAS]) + "," + str(asToCCSize[tAS]) + "," + str(asToIPCCSize[tAS]) + "," + peerDBMap[tAS])
                found = found + 1
            else:
                lost = lost + 1
    outFP.close()
    print("lost " + str(lost))
    print("found " + str(found))

def loadPeerMap():
    retMap = {}
    fp = open(PEER_DB_FILE, "r")
    for line in fp:
        splits = line.split(",")
        if len(splits) == 2:
            retMap[splits[0]] = splits[1]
    fp.close()
    return retMap

def loadCountryMapping():
    retMap = {}
    files = os.listdir(COUNTRY_DIR)
    for tFile in files:
        if "-as.txt" in tFile:
            cc = tFile[0:2]
            fp = open(COUNTRY_DIR + tFile, "r")
            for tASN in fp:
                retMap[tASN.strip()] = cc
            fp.close()
    return retMap

def loadIPMapping():
    retMap = {}
    fp = open(IP_FILE, "r")
    for line in fp:
        splits = line.split(" ")
        if len(splits) == 2:
            retMap[splits[0]] = splits[1].strip()
    fp.close()
    return retMap

def loadASDegree():
    retMap = {}
    fp = open(AS_REL_FILE, "r")
    for line in fp:
        splits = line.split("|")
        if len(splits) == 3:
            lhsAS = splits[0]
            rhsAS = splits[1]
            if not lhsAS in retMap:
                retMap[lhsAS] = 0
            if not rhsAS in retMap:
                retMap[rhsAS] = 0
            retMap[lhsAS] = retMap[lhsAS] + 1
            retMap[rhsAS] = retMap[rhsAS] + 1
    fp.close()
    return retMap

def loadCustConeSize(ipMap):
    retMap = {}
    custMap = loadCust()
    for tProv in custMap:
        print(tProv)
        visited = []
        toVisit = [tProv]
        nextToVisit = []
        while len(toVisit) > 0:
            for tNode in toVisit:
                if tNode in custMap:
                    for tCust in custMap[tNode]:
                        if not (tCust in visited or tCust in toVisit or tCust in nextToVisit):
                            nextToVisit.append(tCust)
                visited.append(tNode)
            toVisit = nextToVisit
            nextToVisit = []
        if ipMap == None:
            retMap[tProv] = len(visited)
        else:
            size = 0
            for tCust in visited:
                if tCust in ipMap:
                    size = size + int(ipMap[tCust])
            retMap[tProv] = size
    return retMap
            
def loadCust():
    retMap = {}
    fp = open(AS_REL_FILE, "r")
    for line in fp:
        splits = line.split("|")
        if len(splits) == 3:
            rel = splits[2].strip()            
            if not (rel == "1" or rel == "-1"):
                continue
            custAS = None
            provAS = None
            if rel == "-1":
                custAS = splits[1]
                provAS = splits[0]
            else:
                custAS = splits[0]
                provAS = splits[1]
            if not provAS in retMap:
                retMap[provAS] = []
            if not custAS in retMap:
                retMap[custAS] = []
            retMap[provAS].append(custAS)
    fp.close()
    return retMap

if __name__ == "__main__":
    main()
