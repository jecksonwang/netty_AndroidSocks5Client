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


