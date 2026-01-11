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

echo "--- STEP 1: Preliminary Requests ---"
curl -X POST "http://$NODE1:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R1", "R3"]'
curl -X POST "http://$NODE2:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R2", "R1"]'
curl -X POST "http://$NODE3:$PORT/api/resource/preliminary" -H "Content-Type: application/json" -d '["R3", "R2"]'
sleep 2

echo -e "\n--- STEP 2: Allocating initial resources ---"
curl -G "http://$NODE1:$PORT/api/resource/acquire" --data-urlencode "resourceId=R1"
curl -G "http://$NODE2:$PORT/api/resource/acquire" --data-urlencode "resourceId=R2"
sleep 2

echo "--- Killing Node 2 (103) ---"
curl -X POST "http://$NODE2:$PORT/api/leave"
echo "Node 2 is now offline. Waiting for other nodes to detect failure..."
sleep 5

echo -e "\n--- STEP 3: Creating Deadlock situation ---"

echo "P3 attempts to acquire R3 (this will trigger deadlock resolution)..."
RESULT=$(curl -s -G "http://$NODE3:$PORT/api/resource/acquire" --data-urlencode "resourceId=R3")

echo -e "\nResult for P3: $RESULT"
if [ "$RESULT" == "DEADLOCK_RELEASED" ]; then
    echo "Lomet detected deadlock and forced P3 to release R3!"
fi