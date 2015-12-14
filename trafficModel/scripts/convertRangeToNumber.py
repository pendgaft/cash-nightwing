#!/usr/bin/env python3

import re

IN_FILE = "../dataMineKnown.csv"
OUT_FILE = "../dataMineValKnown.csv"
BW_PAT = re.compile("([0-9]+)-([0-9]+) ?([MG])bps")

def main():
    inFP = open(IN_FILE, "r")
    outFP = open(OUT_FILE, "w")
    
    foo = 0
    for line in inFP:
        splits = line.split(",")
        if "Speed" in line:
            outFP.write(line)
        else:
            if len(splits) > 1:
                bw = splits[len(splits) - 1].strip()
                bwVal  = 0
                outStr = ""
                for i in range(0, len(splits) - 1):
                    outStr = outStr + str(splits[i]) + ","
                if bw == "1 Tbps+":
                    bwVal = 2000000
                elif bw == "100+ Gbps":
                    bwVal = 500000
                else:
                    match = BW_PAT.search(bw)
                    if match:
                        lhs = int(match.group(1))
                        rhs = int(match.group(2))
                        mean = (lhs + rhs) / 2
                        if match.group(3) == "M":
                            bwVal = mean 
                        else:
                            bwVal = mean * 1000
                    else:
                        print(bw)
                        foo = foo + 1
                outStr = outStr + str(bwVal) + "\n"
                outFP.write(outStr)
    inFP.close()
    outFP.close()
    print(str(foo))



if __name__=="__main__":
    main()
