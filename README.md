# netty_AndroidSocks5Client
android socks5 client based on netty

该项目旨在更加快捷高效的使用NEETY，并集成socks5的相关功能

目前项目进展

1.正常连接功能已经完成

2.socks5连接已经完成

3.自动重连已经完成

============2020年5月27日更新============

1.丰富了连接报错的提示码，详情看cn.jesson.nettyclient.utils.Error

2.添加了支持socks5的定长编码器Socks5FixedLengthFrameDecoder,以回车或者一行为标准的编码器Socks5LineBasedFrameDecoder
  以分隔符为标准的编码器Socks5DelimiterBasedFrameDecoder

3.添加了2种启动客户端的方式，详情看cn.jesson.nettyclient.utils.StartClientUtils

  （1）使用普通线程 startClientWithSimpleThread

  （2）使用后台Service startClientWithServer 注：使用Service方式请在manifest中注册服务
  
============2020年6月2日更新============

1.添加reconnect方法

2.添加了默认的线程切换，保证回调到界面后，都是在主线程

3.将ClientCore改为单例模式，修复部分BUG

============2020年6月17日更新============

1.添加最简单的后台保活方案

2.添加连接中的状态

3.修复某些特定情况下，closeConnect方法不生效的问题

============2020年6月22日更新============

1.修复与服务器建立交互连接后，clientCore获取到的channel是空的问题



