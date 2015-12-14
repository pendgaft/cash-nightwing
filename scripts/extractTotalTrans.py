#!/usr/bin/env python3

TEST_FILE = ""

def main():
    fp = open(TEST_FILE, "r")
    count = 0
    firstSum = 0.0
    secondsum = 0.0
    for line in fp:
        if "###" in line:
            count = count + 1
        elif count == 1:
            splits = line.split(",")
            firstSum = firstSum + float(splits[1])
        elif count == 4:
            splits = line.split(",")
            secondSum = secondSum + float(splits[1])
        elif count > 4:
            break
    fp.close()
    print(str(firstSum))
    print(str(secondSum))

    print(str(4600.0 / firstSum))
    print(str(4600.0 / secondSum))



if__name__ == "__main__":
    main()
