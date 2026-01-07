#!/bin/bash

NODE1="192.168.56.102"
NODE2="192.168.56.103"
NODE3="192.168.56.104"
NODE4="192.168.56.105"
NODE5="192.168.56.106"
PORT="8080"
SLEEP_TIME=4

echo "--- PHASE 1: Building Full Graph Topology ---"
for TARGET in $NODE2 $NODE3 $NODE4 $NODE5
do
    echo "Node $TARGET is joining $NODE1..."
    curl -X POST "http://$TARGET:$PORT/api/join" \
         -H "Content-Type: application/json" \
         -d "{\"host\":\"$NODE1\", \"port\":$PORT}"
    sleep $SLEEP_TIME
done

echo -e "\n--- PHASE 2: Setting Delays (for better visualization) ---"
for IP in $NODE1 $NODE2 $NODE3 $NODE4 $NODE5
do
    curl -X POST "http://$IP:$PORT/api/setDelay?value=1000"
done
sleep 1

echo -e "\n--- PHASE 3: Starting Work on Node 1 ---"
curl -X POST "http://$NODE1:$PORT/api/work/start?amount=100"
sleep $SLEEP_TIME

echo -e "\n--- PHASE 4: Creating a Deadlock Cycle (Nodes 1, 2, 3) ---"

echo "Node 2 requests work from Node 1..."
curl -X POST "http://$NODE2:$PORT/api/work/request" -H "Content-Type: text/plain" -d "$NODE1:$PORT"
sleep 1

echo "Node 3 requests work from Node 2..."
curl -X POST "http://$NODE3:$PORT/api/work/request" -H "Content-Type: text/plain" -d "$NODE2:$PORT"
sleep 1

echo "Node 1 requests work from Node 3..."
curl -X POST "http://$NODE1:$PORT/api/work/request" -H "Content-Type: text/plain" -d "$NODE3:$PORT"
sleep $SLEEP_TIME

echo -e "\n--- PHASE 5: Triggering Deadlock Detection ---"
curl -X POST "http://$NODE4:$PORT/api/lomet/startDetection"
echo -e "\nCheck logs on Node $NODE4 to see the cycle detection."
sleep $SLEEP_TIME

echo -e "\n--- PHASE 6: Fault Tolerance (Kill and Revive) ---"
echo "Killing Node 5..."
curl -X DELETE "http://$NODE5:$PORT/api/kill"
sleep 3

echo "Node 4 tries to sync with Node 5 (will fail and remove it)..."
curl -X POST "http://$NODE4:$PORT/api/lomet/startDetection"
sleep 2

echo "Reviving Node 5..."
curl -X POST "http://$NODE5:$PORT/api/revive"
sleep 1
curl -X POST "http://$NODE5:$PORT/api/join" -H "Content-Type: application/json" -d "{\"host\":\"$NODE1\", \"port\":$PORT}"

echo -e "\n--- Demonstration Finished ---"