# CDI Spring Java Configuration
Reads [Spring Java Configuration classes](http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/beans.html#beans-java) and turns them into CDI bean definitions.

## FAQ

### Does it work yet?
No

### Will it ever?
Dunno

### Is it a good idea™?
No

### Why not?
It's quite hard to get exactly the same semantics in CDI as in Spring.

### Why not just use `@Produces`?
Because then you have to manually keep the Spring and CDI annotations in sync.

### When may I want it use it anyway (should it ever work)?
You have a large EAR with several EJB-JARs and want to avoid having to create an application context for each of them. At the same time you want to use the [Spring TestContext Framework](http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html) for integration tests. You don't want to use [Arquillian](http://arquillian.org/) because that requires you to use ShrinkWrap where you have to repeat the same things you wrote in the POM.

### But Arquillian is a framework platform that let's you build whatever you need
ಠ_ಠ

### What should I use instead?
WAR deployment

### There is a class checked in
Yes, this is used for byte code reverse engineering.

