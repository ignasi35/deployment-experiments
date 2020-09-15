## Deploy the sample code

1. Build the docker image and publish it. From a terminal where you already logged in to dockerhub run the command:

```
sbt -Ddocker.username=ignasi35  docker:publish
```

(check the docs if you use a different docker registry)

2. Setup `kubectl` to use your `minikube` as a cluster:


```
kubectl config use-context minikube
```

3. Use `kubectl` to apply the RBAC, deployment and service:

```
kubectl apply -f deployment/rbac.yml
kubectl apply -f deployment/deployment.yml
kubectl apply -f deployment/service.yml
```

4. Finally, tell minikube to expose the service with an eternal IP:

```
$ kubectl get services
NAME         TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                         AGE
cart         LoadBalancer   10.110.38.135   <pending>     8080:32091/TCP,9090:30620/TCP   14m
kubernetes   ClusterIP      10.96.0.1       <none>        443/TCP                         47m

$ minikube service cart 
|-----------|------|-----------------|-------------------------|
| NAMESPACE | NAME |   TARGET PORT   |           URL           |
|-----------|------|-----------------|-------------------------|
| default   | cart | http/8080       | http://172.17.0.3:32091 |
|           |      | management/9090 | http://172.17.0.3:30620 |
|-----------|------|-----------------|-------------------------|
🏃  Starting tunnel for service cart.
|-----------|------|-------------|------------------------|
| NAMESPACE | NAME | TARGET PORT |          URL           |
|-----------|------|-------------|------------------------|
| default   | cart |             | http://127.0.0.1:64863 |
|           |      |             | http://127.0.0.1:64864 |
|-----------|------|-------------|------------------------|
🎉  Opening service default/cart in default browser...
🎉  Opening service default/cart in default browser...
```

5. Try it with [grpcurl](https://github.com/fullstorydev/grpcurl):

```
# add item to cart
grpcurl -d '{"cartId":"cart1", "itemId":"socks", "quantity":3}' -plaintext 127.0.0.1:64863 shoppingcart.ShoppingCartService.AddItem
```

Or check the Akka HTTP Management port:

```
open http://127.0.0.1:64864/cluster/members 
```