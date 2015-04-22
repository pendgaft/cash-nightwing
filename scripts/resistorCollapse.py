#! /usr/bin/env python3

# This script was used to deal with the HUGE number of ASes listed for
# Brazil, it will work for any country with that same issue
# sim merge basically strips out all ASes without:
#  1) Any IP blocks
#  2) Who are not multihomed (trivial routing decisions)
#  3) Who are only homed in other wardern ASes (for the most part can be pruned, we lose a little bit of power when we reverse poison)

import argparse
import os

IP_FILE = "../realTopo/whole-internet-20150101-ip.txt"
AS_REL_FILE = "../realTopo/20150201.as-rel.txt"

def nullPrune():
    relTup = loadRelMap()
    custMap = relTup[0]
    provMap = relTup[0]
    allAS = set([])
    notPrunedSet = set([])
    for tAS in custMap:
        allAS.add(tAS)
    for tAS in provMap:
        allAS.add(tAS)
    for tAS in custMap:
        if len(custMap[tAS]) > 0:
            notPrunedSet.add(tAS)
    print("all " + str(len(allAS)))
    print("not pruned " + str(len(notPrunedSet)))
    

def filter(countryFile, thresh):
    ipMap = loadIPMap()
    asSet = loadASSet(countryFile)
    prunedDownSet = meetingThreshSet(ipMap, asSet, thresh)
    newFileName = countryFile + "-" + str(thresh)
    filePtr = open(newFileName, "w")
    for tASN in prunedDownSet:
        filePtr.write(str(tASN) + "\n")
    filePtr.close()

def explore(countryFile):
    ipMap = loadIPMap()
    asSet = loadASSet(countryFile)
    print("total ases " + str(len(asSet)) + " with ip count " + str(totalIPCount(ipMap, asSet)))
    print("exists in ip map " + str(len(meetingThreshSet(ipMap, asSet, 0))))
    print("greater than 0 " + str(len(meetingThreshSet(ipMap, asSet, 1))))
    print("greater than 1 " + str(len(meetingThreshSet(ipMap, asSet, 2))))
    print("5 or more " + str(len(meetingThreshSet(ipMap, asSet, 5))) + " with ip count " + str(totalIPCount(ipMap, meetingThreshSet(ipMap, asSet, 5))))
    print("10 or more " + str(len(meetingThreshSet(ipMap, asSet, 10))) + " with ip count " + str(totalIPCount(ipMap, meetingThreshSet(ipMap, asSet, 10))))
    
def simMerge(countryFile):
    ipMap = loadIPMap()
    relTup = loadRelMap()
    custMap = relTup[0]
    provMap = relTup[1]
    countrySet = loadASSet(countryFile)
    print("done loading")
    noCust = set([])
    singleProv = set([])
    noIPs = set([])

    for tAS in countrySet:
        if (not tAS in custMap) or len(custMap[tAS]) == 0:
            noCust.add(tAS)
        if (not tAS in provMap) or len(provMap[tAS]) < 2:
            singleProv.add(tAS)
        if (not tAS in ipMap) or ipMap[tAS] == 0:
            noIPs.add(tAS)
    print ("total BR ASes " + str(len(countrySet)))
    print ("no cust " + str(len(noCust)))
    print ("at most single prov " + str(len(singleProv)))
    print ("happy interestction " + str(len(singleProv.intersection(noCust))))
    print ("no ips " + str(len(noIPs)))

    noCust.difference_update(noIPs)

    
    lost = 0
    for tAS in noCust:
        lost += ipMap[tAS]
    print("lost " + str(lost))
    allIPs = 0
    brIP = 0
    for tAS in ipMap:
        if tAS in countrySet:
            brIP += ipMap[tAS]
        allIPs += ipMap[tAS]
    print("all " + str(allIPs))
    print("br " + str(brIP))

    testSet = set([])
    for tAS in countrySet:
        if not tAS in provMap: 
            testSet.add(tAS)
        elif len(provMap[tAS]) < 2:
            testSet.add(tAS)
        else:
            outsideWarden = False
            for tProv in provMap[tAS]:
                if not tProv in countrySet:
                    outsideWarden = True
                    break
            if not outsideWarden:
                testSet.add(tAS)
    noCustNoMultihome = noCust.intersection(testSet)
    countrySet.difference_update(noCustNoMultihome)
    countrySet.difference_update(noIPs)
    print(str(len(countrySet)))
    print(str(len(noCustNoMultihome)))

    filePtr = open("BR-noCustNoExternalMultihome.txt", "w")
    for tAS in countrySet:
        filePtr.write(tAS + "\n")
    filePtr.close()
    
def totalIPCount(ipMap, asSet):
    sum = 0
    for tASN in asSet:
        if tASN in ipMap:
            sum += ipMap[tASN]
    return sum

def meetingThreshSet(ipMap, asSet, minIP):
    meetingSet = []
    for tASN in asSet:
        if tASN in ipMap and ipMap[tASN] >= minIP:
            meetingSet.append(tASN)
    return set(meetingSet)


def loadASSet(countryFile):
    asList = []
    filePtr = open(countryFile, "r")
    for line in filePtr:
        if len(line.strip()) > 0:
            asList.append(line.strip())
    filePtr.close()
    return set(asList)

def loadIPMap():
    baseMap = {}
    filePtr = open(IP_FILE, "r")
    for line in filePtr:
        tokens = line.split(" ")
        if not len(tokens) == 2:
            continue
        baseMap[tokens[0]] = int(tokens[1])
    filePtr.close()
    return baseMap

def loadRelMap():
    provMap = {}
    custMap = {}
    filePtr = open(AS_REL_FILE, "r")
    for line in filePtr:
        if line[0] == "#":
            continue
        tokens = line.strip().split("|")
        if len(tokens) != 3:
            continue
        cust = None
        prov = None
        if tokens[2] == "-1":
            prov = tokens[0]
            cust = tokens[1]
        elif tokens[2] == "1":
            prov = tokens[1]
            cust = tokens[0]
        if not (cust == None or prov == None):
           if not cust in provMap:
                provMap[cust] = set([])
                custMap[cust] = set([])
           if not prov in provMap:
                provMap[prov] = set([])
                custMap[prov] = set([])
           provMap[cust].add(prov)
           custMap[prov].add(cust)
    filePtr.close()
    return (custMap, provMap)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--country_file", help="Base country file", required=True)
    args = parser.parse_args()

    #explore(args.country_file)
    #filter(args.country_file, 10)
    simMerge(args.country_file)
