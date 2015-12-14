#!/usr/bin/env python3

import makeASDataMine

KNOWN_FILE = "../dataMineValKnown.csv"
OUT_FILE = "../toClass.csv"

def main():
    asToCC = makeASDataMine.loadCountryMapping()
    asToIP = makeASDataMine.loadIPMapping()
    asToDeg = makeASDataMine.loadASDegree()
    asToCCSize = makeASDataMine.loadCustConeSize(None)
    asToIPCCSize = makeASDataMine.loadCustConeSize(asToIP)
    alreadyKnown = loadAlreadyClass()
    
    fp = open(OUT_FILE, "w")
    fp.write("ASN,Country,IP Size,Degree,CC Size (AS),CC Size (IP),Speed\n")
    for tKey in asToDeg:
        if not tKey in alreadyKnown:
            cc = "XX"
            if tKey in asToCC:
                cc = asToCC[tKey]
            ip = 0
            if tKey in asToIP:
                ip = asToIP[tKey]
            coneSize = 1
            if tKey in asToCCSize:
                coneSize = asToCCSize[tKey]
            ipConeSize = ip
            if tKey in asToIPCCSize:
                ipConeSize = asToIPCCSize[tKey]
            fp.write(tKey + "," + cc + "," + str(ip) + "," + str(asToDeg[tKey]) + "," + str(coneSize) + "," + str(ipConeSize) + ",?\n")
    fp.close()
        

def loadAlreadyClass():
    retList = []
    fp = open(KNOWN_FILE, "r")
    for line in fp:
        splits = line.split(",")
        if len(splits) > 1:
            retList.append(splits[0])
    fp.close()
    return retList

if __name__ == "__main__":
    main()
