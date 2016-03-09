#!/usr/bin/env python3

BASE_FILE = "../countryMappings/US-asInt.txt"
INT_FILE = "../realTopo/int-as.txt"
REALLY_INT_FILE = "../realTopo/int-int-as.txt"

def main():
    intSet = loadFlatFile(INT_FILE)
    reallyIntSet = loadFlatFile(REALLY_INT_FILE)
    countrySet = loadFlatFile(BASE_FILE)

    writeSet(BASE_FILE + "-int", intSet.intersection(countrySet))
    writeSet(BASE_FILE + "-reallyInt", reallyIntSet.intersection(countrySet))

def writeSet(fileName, outSet):
    fp = open(fileName, "w")
    for tItem in outSet:
        fp.write(str(tItem) + "\n")
    fp.close()
    
def loadFlatFile(fileName):
    retSet = set([])
    fp = open(fileName, "r")
    for line in fp:
        line = line.strip()
        if len(line) > 0:
            retSet.add(line)
    fp.close()
    return retSet




if __name__ == "__main__":
    main()
