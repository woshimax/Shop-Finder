# Shop-Finder基本功能  
## 一、短息校验登陆  
传统session登陆弊端：在多服务器（分布式）情况下，登陆信息难以实现共享和同步  
核心功能：改进基本的session登陆，使用redis和双重拦截器完成登陆校验和登陆状态的刷新，其中利用redis来实现登陆信息的多服务器共享和同步  
