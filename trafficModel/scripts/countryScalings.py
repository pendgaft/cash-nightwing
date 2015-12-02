#!/usr/bin/env python3

import csv

ISO_FILE = "../../iso_3166_2_countries.csv"
POPULATION_FILE = "../sp.pop.totl_Indicator_en_csv_v2.csv"
INTERNET_USERS_FILE = "../it.net.user.p2_Indicator_en_csv_v2.csv"
SECURE_SERVERS_FILE = "../it.net.secr.p6_Indicator_en_csv_v2.csv"
COUNTRY_CONT_FILE = "../country_continent.csv"
BW_FILE = "../trafficByCont.csv"

def main():
    cc32Mapping = buildThreeToTwoMapping()
    ccToCont = buildCountryToContMapping()
    popMapping = loadWorldBank(POPULATION_FILE, cc32Mapping)
    inetUserMapping = loadWorldBank(INTERNET_USERS_FILE, cc32Mapping)
    sereverMapping = loadWorldBank(SECURE_SERVERS_FILE, cc32Mapping)
    upBW = loadContData(1)
    downBW = loadContData(2)
    smash(inetUserMapping, popMapping, ccToCont, upBW, "../ccUpBW.csv")
    smash(inetUserMapping, popMapping, ccToCont, downBW, "../ccDownBW.csv")
    
    
def smash(inetUserMapping, popMapping, ccToCont, bwMap, outFileName):
    outFP = open(outFileName, "w")
    no32 = 0
    for cc in inetUserMapping:
        myVal = inetUserMapping[cc] * popMapping[cc] / 100.0 * bwMap[ccToCont[cc]]
        outFP.write(cc + "," + str(myVal) + "\n")
    outFP.close()
        

def buildCountryToContMapping():
    retMap = {}
    with open(COUNTRY_CONT_FILE, "r") as f:
        reader = csv.reader(f, quotechar='"', delimiter=',', quoting=csv.QUOTE_ALL, skipinitialspace=True)
        for line in reader:
            retMap[line[0]] = line[1]
    return retMap

def loadContData(col):
    retMap = {}
    with open(BW_FILE, "r") as f:
        reader = csv.reader(f, quotechar='"', delimiter=',', quoting=csv.QUOTE_ALL, skipinitialspace=True)
        for line in reader:
            retMap[line[0]] = float(line[col])
    return retMap

def buildThreeToTwoMapping():
    retMap = {}
    with open(ISO_FILE, "r") as f:
        reader = csv.reader(f, quotechar='"', delimiter=',', quoting=csv.QUOTE_ALL, skipinitialspace=True)
        for line in reader:
            retMap[line[11]] = line[10]
    return retMap

def loadWorldBank(wbFile, threeToTwo):
    retMap = {}
    badCount = 0
    emptyCount = 0
    with open(wbFile, "r") as f:
        reader = csv.reader(f, quotechar='"', delimiter=',', quoting=csv.QUOTE_ALL, skipinitialspace=True)
        for i in range(0, 5):
            line = next(reader)  
        for line in reader:
            threeCC = line[1]
            myData = line[len(line) - 3]
            if len(myData.strip()) == 0:
                emptyCount = emptyCount + 1
                continue
            if threeCC in threeToTwo:
                retMap[threeToTwo[threeCC]] = float(myData)
            else:
                badCount = badCount + 1
    print("bad for " + wbFile + " is " + str(badCount) + " and empty count of " + str(emptyCount))
    return retMap

if __name__ == "__main__":
    main()
