#!/bin/bash

NODE1="192.168.56.102"
NODE2="192.168.56.103"
NODE3="192.168.56.104"
NODE4="192.168.56.105"
NODE5="192.168.56.106"
PORT="8080"
SLEEP_TIME=1

echo "--- PHASE 1: Building Full Graph Topology ---"
for TARGET in $NODE2 $NODE3 $NODE4 $NODE5
do
    echo "Node $TARGET is joining $NODE1..."
    curl -X POST "http://$TARGET:$PORT/api/join" \
         -H "Content-Type: application/json" \
         -d "{\"host\":\"$NODE1\", \"port\":$PORT}"
    sleep $SLEEP_TIME
done

curl -X POST "http://$NODE1:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R1", "R3"]'
curl -X POST "http://$NODE2:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R2", "R1"]'
curl -X POST "http://$NODE3:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R3", "R2"]'

curl "http://$NODE2:$PORT/api/resource/acquire" &
sleep 2

curl "http://$NODE1:$PORT/api/resource/acquire" &
sleep 2

curl -X POST "http://$NODE1:$PORT/api/resource/release?resourceId=R1"

#curl "http://$NODE2:$PORT/api/resource/acquire"